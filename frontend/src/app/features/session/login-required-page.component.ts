import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  ViewChild,
  inject,
} from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { contextualLoginUrl } from '../../core/session/contextual-login-url';

@Component({
  selector: 'app-login-required-page',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './login-required-page.component.html',
  styleUrl: './login-required-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginRequiredPageComponent implements AfterViewInit {
  private readonly route = inject(ActivatedRoute);

  @ViewChild('pageTitle', { static: true }) private pageTitle?: ElementRef<HTMLHeadingElement>;

  readonly loginUrl = contextualLoginUrl(this.route.snapshot.queryParamMap.get('returnTo') ?? '/');

  ngAfterViewInit(): void {
    queueMicrotask(() => this.pageTitle?.nativeElement.focus());
  }
}
