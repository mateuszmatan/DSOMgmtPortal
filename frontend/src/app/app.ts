import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'dso-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly navigation = [
    {
      path: '/products',
      label: 'DevSecOps Product Management',
      hint: 'Products, services, pipelines and keys',
    },
    {
      path: '/monitoring',
      label: 'DevSecOps Pipeline Monitoring',
      hint: 'Pipeline status and DORA metrics',
    },
    {
      path: '/evidence',
      label: 'DevSecOps Change Evidence',
      hint: 'Builds, tests and scans for ServiceNow changes',
    },
    {
      path: '/settings',
      label: 'DevSecOps Global Settings',
      hint: 'Tools, policy and defaults of every pipeline',
    },
  ];
}
