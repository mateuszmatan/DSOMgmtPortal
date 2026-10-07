import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { App } from './app';
import { SECTIONS } from './core/sections';
import { text } from './testing/dom';

@Component({ template: '' })
class Blank {}

describe('App', () => {
  let fixture: ComponentFixture<App>;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([{ path: '**', component: Blank }])],
    });
    fixture = TestBed.createComponent(App);
    await fixture.whenStable();
  });

  const page = () => fixture.nativeElement as HTMLElement;
  const triggers = () => [...page().querySelectorAll<HTMLButtonElement>('nav.menu button')];

  async function open(label: string): Promise<HTMLElement> {
    triggers()
      .find((trigger) => text(trigger) === label)!
      .click();
    await fixture.whenStable();
    return [...document.querySelectorAll<HTMLElement>('.mat-mdc-menu-panel')].at(-1)!;
  }

  it('names the portal in its header and offers the Beadle and DevSecOps Management menus', () => {
    expect(page().querySelector('.brand-name')?.textContent).toBe(
      'BBH DevSecOps Management Portal',
    );
    expect(triggers().map(text)).toEqual(['Beadle', 'DevSecOps Management']);
  });

  it.each([
    [
      'Beadle',
      ['Overview', 'Product Onboarding', 'Production Change'],
      ['/beadle', '/beadle/onboarding', '/beadle/changes'],
    ],
    [
      'DevSecOps Management',
      ['Product Management', 'Pipeline Monitoring', 'Change Evidence', 'Global Settings'],
      ['/products', '/monitoring', '/evidence', '/settings'],
    ],
  ])('opens %s with the links of its sections in order', async (label, labels, paths) => {
    const panel = await open(label);
    const links = [...panel.querySelectorAll('a[mat-menu-item]')];

    expect(panel.getAttribute('aria-label')).toBe(label);
    expect(links.map(text)).toEqual(labels);
    expect(links.map((link) => link.getAttribute('href'))).toEqual(paths);
  });

  it('keeps the descriptions of the sections and icons out of the main menu', async () => {
    const panel = await open('DevSecOps Management');

    expect(SECTIONS.map((section) => section.description)).toEqual([
      'Products, services, pipelines and keys',
      'Pipeline status and DORA metrics',
      'Builds, tests and scans for ServiceNow changes',
      'Tools, policy and defaults of every pipeline',
    ]);
    expect(SECTIONS.some((section) => text(panel).includes(section.description))).toBe(false);
    expect(page().querySelector('header mat-icon, header .material-icons')).toBeNull();
    expect(panel.querySelector('mat-icon, .mat-mdc-menu-submenu-icon')).toBeNull();
  });

  it('marks the menu and the item of the current page', async () => {
    await TestBed.inject(Router).navigateByUrl('/monitoring');
    await fixture.whenStable();

    expect(text(page().querySelector('.menu-group.active'))).toBe('DevSecOps Management');
    expect(text((await open('DevSecOps Management')).querySelector('a.active'))).toBe(
      'Pipeline Monitoring',
    );

    await TestBed.inject(Router).navigateByUrl('/beadle/onboarding');
    await fixture.whenStable();

    expect(text(page().querySelector('.menu-group.active'))).toBe('Beadle');
    expect(text((await open('Beadle')).querySelector('a.active'))).toBe('Product Onboarding');
  });

  it('gives every section the full DevSecOps name as its page heading', () => {
    expect(SECTIONS.map((section) => section.heading)).toEqual([
      'DevSecOps Product Management',
      'DevSecOps Pipeline Monitoring',
      'DevSecOps Change Evidence',
      'DevSecOps Global Settings',
    ]);
  });

  it('has an empty footer apart from the year mark', () => {
    expect(page().querySelector('footer')?.textContent?.trim()).toBe('BBH 2026');
  });
});
