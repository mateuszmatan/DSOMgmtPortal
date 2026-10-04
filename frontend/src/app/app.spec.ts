import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [App], providers: [provideRouter([])] });
  });

  it('names the portal in its header and offers both tabs', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const tabs = [...page.querySelectorAll('a.tab')];

    expect(page.querySelector('.brand-name')?.textContent).toBe('BBH DevSecOps Management Portal');
    expect(tabs.map((tab) => tab.textContent?.trim())).toEqual([
      'Product Management',
      'Pipeline Monitoring',
    ]);
    expect(tabs.map((tab) => tab.getAttribute('href'))).toEqual(['/products', '/monitoring']);
  });

  it('has an empty footer apart from the year mark', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('footer')?.textContent?.trim(),
    ).toBe('BBH 2026');
  });
});
