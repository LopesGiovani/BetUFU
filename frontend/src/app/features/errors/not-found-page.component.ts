import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  ViewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found-page',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './not-found-page.component.html',
  styleUrl: './not-found-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotFoundPageComponent implements AfterViewInit {
  @ViewChild('pageTitle', { static: true }) private pageTitle?: ElementRef<HTMLHeadingElement>;

  ngAfterViewInit(): void {
    queueMicrotask(() => this.pageTitle?.nativeElement.focus());
  }
}
