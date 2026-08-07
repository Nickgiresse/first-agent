import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'afb-button',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [],
  templateUrl: './button.html',
  styleUrl: './button.scss',
})
export class Button {}
