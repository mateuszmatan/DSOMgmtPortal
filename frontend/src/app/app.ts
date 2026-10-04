import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

/** The portal shell: a navy header with the portal name and its tabs, the current page and a navy footer. */
@Component({
  selector: 'dso-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly navigation = [
    { path: '/products', label: 'Product Management', hint: 'Products, services, pipelines, keys' },
    { path: '/monitoring', label: 'Pipeline Monitoring', hint: 'Status and DORA metrics' },
  ];
}
