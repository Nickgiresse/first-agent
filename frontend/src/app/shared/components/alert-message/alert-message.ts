import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'afb-alert-message',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [],
  templateUrl: './alert-message.html',
  styleUrl: './alert-message.scss',
})
export class AlertMessage {}
