import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'afb-stepper',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [],
  templateUrl: './stepper.html',
  styleUrl: './stepper.scss',
})
export class Stepper {}
