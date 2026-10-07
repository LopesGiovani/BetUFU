import { Injectable, inject } from '@angular/core';
import { ChatService } from '../../api/generated/api/chat.service';
import type { ChatSanctionCommand } from '../../api/generated/model/chat-sanction-command';
import type { ChatReviewCommand } from '../../api/generated/model/chat-review-command';

const options = { transferCache: false };

@Injectable()
export class ChatModerationApiAdapter {
  private readonly chat = inject(ChatService);
  cases() {
    return this.chat.getChatModerationCases('body', false, options);
  }
  reviews() {
    return this.chat.getPendingChatReviews('body', false, options);
  }
  sanctions() {
    return this.chat.getOwnChatSanctions('body', false, options);
  }
  evidence(caseId: string, reason: string) {
    return this.chat.getChatModerationEvidence({ caseId, reason }, 'body', false, options);
  }
  forward(caseId: string, reason: string, clientCommandId: string) {
    return this.chat.forwardChatModerationCase(
      { caseId, chatModerationCommand: { reason, clientCommandId } },
      'body',
      false,
      options,
    );
  }
  remove(caseId: string, reason: string, clientCommandId: string) {
    return this.chat.removeChatModerationMessage(
      { caseId, chatModerationCommand: { reason, clientCommandId } },
      'body',
      false,
      options,
    );
  }
  suggestion(caseId: string) {
    return this.chat.suggestChatSanction({ caseId }, 'body', false, options);
  }
  sanction(
    caseId: string,
    reason: string,
    clientCommandId: string,
    kind: ChatSanctionCommand.KindEnum,
  ) {
    return this.chat.confirmChatSanction(
      { caseId, chatSanctionCommand: { reason, clientCommandId, kind } },
      'body',
      false,
      options,
    );
  }
  requestReview(sanctionId: string, reason: string, clientCommandId: string) {
    return this.chat.requestChatSanctionReview(
      { sanctionId, chatModerationCommand: { reason, clientCommandId } },
      'body',
      false,
      options,
    );
  }
  decide(
    reviewId: string,
    reason: string,
    clientCommandId: string,
    decision: ChatReviewCommand.DecisionEnum,
  ) {
    return this.chat.decideChatSanctionReview(
      { reviewId, chatReviewCommand: { reason, clientCommandId, decision } },
      'body',
      false,
      options,
    );
  }
}
