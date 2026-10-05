import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { SECTIONS } from './core/sections';

describe('App', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [App], providers: [provideRouter([])] });
  });

  async function render(): Promise<HTMLElement> {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('names the portal in its header and offers the four sections in order', async () => {
    const page = await render();
    const links = [...page.querySelectorAll('nav.menu a.menu-link')];

    expect(page.querySelector('.brand-name')?.textContent).toBe('BBH DevSecOps Management Portal');
    expect(links.map((link) => link.textContent?.trim())).toEqual([
      'Product Management',
      'Pipeline Monitoring',
      'Change Evidence',
      'Global Settings',
    ]);
    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      '/products',
      '/monitoring',
      '/evidence',
      '/settings',
    ]);
  });

  it('keeps the descriptions of the sections out of the main menu', async () => {
    const menu = (await render()).querySelector('nav.menu')!;

    expect(SECTIONS.map((section) => section.description)).toEqual([
      'Products, services, pipelines and keys',
      'Pipeline status and DORA metrics',
      'Builds, tests and scans for ServiceNow changes',
      'Tools, policy and defaults of every pipeline',
    ]);
    expect(SECTIONS.some((section) => menu.textContent?.includes(section.description))).toBe(false);
    expect(menu.querySelector('mat-icon')).toBeNull();
  });

  it('gives every section the full DevSecOps name as its page heading', () => {
    expect(SECTIONS.map((section) => section.heading)).toEqual([
      'DevSecOps Product Management',
      'DevSecOps Pipeline Monitoring',
      'DevSecOps Change Evidence',
      'DevSecOps Global Settings',
    ]);
  });

  it('has an empty footer apart from the year mark', async () => {
    expect((await render()).querySelector('footer')?.textContent?.trim()).toBe('BBH 2026');
  });
});
