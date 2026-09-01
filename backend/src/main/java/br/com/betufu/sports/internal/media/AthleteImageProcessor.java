package br.com.betufu.sports.internal.media;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.sports.internal.media.AthleteMediaModels.ProcessedImage;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("runtime | integrated")
public final class AthleteImageProcessor {

  public static final int DEFAULT_MAX_BYTES = 5 * 1024 * 1024;
  public static final int DEFAULT_MAX_PIXELS = 4_000_000;

  private final int maxBytes;
  private final int maxPixels;

  public AthleteImageProcessor() {
    this(DEFAULT_MAX_BYTES, DEFAULT_MAX_PIXELS);
  }

  @Autowired
  public AthleteImageProcessor(
      @Value("${betufu.media.max-bytes:5242880}") int maxBytes,
      @Value("${betufu.media.max-pixels:4000000}") int maxPixels) {
    if (maxBytes <= 0 || maxPixels <= 0) {
      throw new IllegalArgumentException("media limits must be positive");
    }
    this.maxBytes = maxBytes;
    this.maxPixels = maxPixels;
  }

  public ProcessedImage process(byte[] input, String declaredContentType) {
    if (input == null || input.length == 0 || input.length > maxBytes) {
      throw invalidMedia();
    }
    String expectedType = normalizeType(declaredContentType);
    if (!isSupportedType(expectedType)) {
      throw unsupportedMedia();
    }

    try (ImageInputStream imageInput =
        ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
      if (imageInput == null) {
        throw invalidMedia();
      }
      Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
      if (!readers.hasNext()) {
        throw invalidMedia();
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(imageInput, true, true);
        String actualType = normalizeType(toContentType(reader.getFormatName()));
        if (!expectedType.equals(actualType)) {
          throw invalidMedia();
        }
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        if (!withinPixelLimit(width, height)) {
          throw invalidMedia();
        }
        BufferedImage decoded = reader.read(0);
        if (decoded == null || decoded.getWidth() != width || decoded.getHeight() != height) {
          throw invalidMedia();
        }
        rejectTrailingPayload(input, actualType);
        byte[] encoded = reencode(decoded, actualType);
        if (encoded.length == 0 || encoded.length > maxBytes) {
          throw invalidMedia();
        }
        return new ProcessedImage(encoded, actualType, width, height);
      } finally {
        reader.dispose();
      }
    } catch (ApiProblemException e) {
      throw e;
    } catch (IOException | RuntimeException e) {
      throw invalidMedia();
    }
  }

  private byte[] reencode(BufferedImage decoded, String contentType) throws IOException {
    BufferedImage target = decoded;
    if ("image/jpeg".equals(contentType) && decoded.getColorModel().hasAlpha()) {
      target =
          new BufferedImage(decoded.getWidth(), decoded.getHeight(), BufferedImage.TYPE_INT_RGB);
      Graphics2D graphics = target.createGraphics();
      try {
        graphics.setComposite(AlphaComposite.Src);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, target.getWidth(), target.getHeight());
        graphics.drawImage(decoded, 0, 0, null);
      } finally {
        graphics.dispose();
      }
    }

    String format = "image/jpeg".equals(contentType) ? "jpeg" : "png";
    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
    if (!writers.hasNext()) {
      throw new IOException("no image writer");
    }
    ImageWriter writer = writers.next();
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
      writer.setOutput(imageOutput);
      ImageWriteParam param = writer.getDefaultWriteParam();
      if ("jpeg".equals(format) && param.canWriteCompressed()) {
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.9f);
      }
      writer.write(null, new javax.imageio.IIOImage(target, null, null), param);
    } finally {
      writer.dispose();
    }
    return output.toByteArray();
  }

  private static void rejectTrailingPayload(byte[] input, String contentType) {
    if ("image/png".equals(contentType)) {
      byte[] marker = {(byte) 0x49, (byte) 0x45, (byte) 0x4e, (byte) 0x44};
      int markerOffset = lastIndexOf(input, marker);
      if (markerOffset < 0 || markerOffset + 8 != input.length) {
        throw invalidMedia();
      }
    } else {
      int markerOffset = lastIndexOf(input, new byte[] {(byte) 0xff, (byte) 0xd9});
      if (markerOffset < 0 || markerOffset + 2 != input.length) {
        throw invalidMedia();
      }
    }
  }

  private static int lastIndexOf(byte[] input, byte[] marker) {
    outer:
    for (int i = input.length - marker.length; i >= 0; i--) {
      for (int j = 0; j < marker.length; j++) {
        if (input[i + j] != marker[j]) {
          continue outer;
        }
      }
      return i;
    }
    return -1;
  }

  private boolean withinPixelLimit(int width, int height) {
    return width > 0 && height > 0 && ((long) width * height) <= maxPixels;
  }

  private static String normalizeType(String contentType) {
    return contentType == null ? "" : contentType.trim().toLowerCase(java.util.Locale.ROOT);
  }

  private static String toContentType(String formatName) {
    return switch (normalizeType(formatName)) {
      case "jpg", "jpeg" -> "image/jpeg";
      case "png" -> "image/png";
      default -> "";
    };
  }

  private static boolean isSupportedType(String type) {
    return "image/jpeg".equals(type) || "image/png".equals(type);
  }

  private static ApiProblemException invalidMedia() {
    return new ApiProblemException(HttpStatusHolder.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
  }

  private static ApiProblemException unsupportedMedia() {
    return new ApiProblemException(
        HttpStatusHolder.UNSUPPORTED, ApiErrorCode.UNSUPPORTED_MEDIA_TYPE);
  }

  private static final class HttpStatusHolder {
    private static final org.springframework.http.HttpStatus BAD_REQUEST =
        org.springframework.http.HttpStatus.BAD_REQUEST;
    private static final org.springframework.http.HttpStatus UNSUPPORTED =
        org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE;
  }
}
