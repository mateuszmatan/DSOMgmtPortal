import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { App } from './app';
import { MENUS, SECTIONS } from './core/sections';
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
      ['Changes', 'New Change', 'Admin'],
      ['/beadle/changes', '/beadle/new-change', '/beadle/admin'],
    ],
    [
      'DevSecOps Management',
      ['Self-service', 'Pipeline Monitoring', 'Change Evidence', 'Admin'],
      ['/self-service', '/monitoring', '/evidence', '/admin'],
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
      'Set up or change the DevSecOps pipelines of your product, step by step',
      'Pipeline status and DORA metrics',
      'Builds, tests and scans for ServiceNow changes',
      'Departments, products, services and the DSOEnhanced library defaults',
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

    await TestBed.inject(Router).navigateByUrl('/beadle/admin/products/2');
    await fixture.whenStable();

    expect(text(page().querySelector('.menu-group.active'))).toBe('Beadle');
    expect(text((await open('Beadle')).querySelector('a.active'))).toBe('Admin');
  });

  it('marks Changes of Beadle on the page and the edit page of a change', async () => {
    await TestBed.inject(Router).navigateByUrl('/beadle/changes/4/edit');
    await fixture.whenStable();

    expect(text(page().querySelector('.menu-group.active'))).toBe('Beadle');
    expect(text((await open('Beadle')).querySelector('a.active'))).toBe('Changes');
  });

  it('gives the Beadle sections their ProTech headings', () => {
    expect(MENUS[0].sections.map((section) => [section.heading, section.description])).toEqual([
      [
        'ProTech Changes',
        'The ProTech changes of your department, read from ProTech each time you open them',
      ],
      [
        'New ProTech Change',
        'Raise a ProTech change (CHG) with its change tasks (CTASK), written from Jira',
      ],
      ['Beadle Admin', 'Departments, products and the change template of each product'],
    ]);
  });

  it('marks Admin of DevSecOps Management on the product pages', async () => {
    await TestBed.inject(Router).navigateByUrl('/admin/products/2/edit');
    await fixture.whenStable();

    expect(text(page().querySelector('.menu-group.active'))).toBe('DevSecOps Management');
    expect(text((await open('DevSecOps Management')).querySelector('a.active'))).toBe('Admin');
  });

  it('gives every section the full DevSecOps name as its page heading', () => {
    expect(SECTIONS.map((section) => section.heading)).toEqual([
      'DevSecOps Self-service',
      'DevSecOps Pipeline Monitoring',
      'DevSecOps Change Evidence',
      'DevSecOps Admin',
    ]);
  });

  it('has an empty footer apart from the year mark', () => {
    expect(page().querySelector('footer')?.textContent?.trim()).toBe('BBH 2026');
  });
});
