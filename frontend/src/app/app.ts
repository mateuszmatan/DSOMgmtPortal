import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatMenuModule } from '@angular/material/menu';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MENUS } from './core/sections';

@Component({
  selector: 'dso-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatMenuModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly menus = MENUS;
}
