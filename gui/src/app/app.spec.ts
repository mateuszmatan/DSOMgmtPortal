import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [App], providers: [provideRouter([])] });
  });

  it('names the portal in its header and offers the four tabs in order', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const tabs = [...page.querySelectorAll('a.tab')];

    expect(page.querySelector('.brand-name')?.textContent).toBe('BBH DevSecOps Management Portal');
    expect(tabs.map((tab) => tab.querySelector('.tab-label')?.textContent?.trim())).toEqual([
      'DevSecOps Product Management',
      'DevSecOps Pipeline Monitoring',
      'DevSecOps Change Evidence',
      'DevSecOps Global Settings',
    ]);
    expect(tabs.map((tab) => tab.getAttribute('href'))).toEqual([
      '/products',
      '/monitoring',
      '/evidence',
      '/settings',
    ]);
  });

  it('gives every tab a hint', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const tabs = [...(fixture.nativeElement as HTMLElement).querySelectorAll('a.tab')];

    expect(tabs.map((tab) => tab.querySelector('.tab-hint')?.textContent?.trim())).toEqual([
      'Products, services, pipelines and keys',
      'Pipeline status and DORA metrics',
      'Builds, tests and scans for ServiceNow changes',
      'Tools, policy and defaults of every pipeline',
    ]);
    expect(
      tabs.every(
        (tab) => tab.getAttribute('title') === tab.querySelector('.tab-hint')?.textContent?.trim(),
      ),
    ).toBe(true);
  });

  it('has an empty footer apart from the year mark', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('footer')?.textContent?.trim(),
    ).toBe('BBH 2026');
  });
});
