import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'afb-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [],
  templateUrl: './header.html',
  styleUrl: './header.scss',
})
export class Header {}
