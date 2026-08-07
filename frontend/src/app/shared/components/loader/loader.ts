import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'afb-loader',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [],
  templateUrl: './loader.html',
  styleUrl: './loader.scss',
})
export class Loader {}
