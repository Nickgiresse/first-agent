import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'afb-footer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [],
  templateUrl: './footer.html',
  styleUrl: './footer.scss',
})
export class Footer {}
