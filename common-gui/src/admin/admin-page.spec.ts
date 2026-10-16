import { Component, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Route, Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AdminArea } from '../core/sections';
import { text } from '../testing/dom';
import { AdminPage } from './admin-page';

const DEVSECOPS_ADMIN: AdminArea = {
  section: {
    path: '/admin',
    label: 'Admin',
    heading: 'DevSecOps Admin',
    description:
      'Set up the portal for everyone: departments, products and their services, what a new service gets, and the settings every pipeline shares.',
  },
  tabs: [
    { path: '/admin/departments', label: 'Departments' },
    { path: '/admin/products', label: 'Products' },
    { path: '/admin/template', label: 'Service template' },
    { path: '/admin/settings', label: 'Library defaults' },
  ],
};

const INVENTORY_ADMIN: AdminArea = {
  section: {
    path: '/inventory/admin',
    label: 'Admin',
    heading: 'Inventory Admin',
    description: 'Departments and the products each one keeps in the inventory.',
  },
  tabs: [
    { path: '/inventory/admin/departments', label: 'Departments' },
    { path: '/inventory/admin/products', label: 'Products' },
  ],
};

@Component({ template: '<p class="tab-content">{{ tab() }}</p>' })
class TabContent {
  readonly tab = input('');
}

function adminRoute(area: AdminArea): Route {
  return {
    path: area.section.path.slice(1),
    component: AdminPage,
    data: { area },
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'products' },
      ...area.tabs.map((tab) => ({
        path: tab.path.split('/').at(-1),
        component: TabContent,
        data: { tab: tab.label },
      })),
    ],
  };
}

describe('AdminPage', () => {
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [adminRoute(DEVSECOPS_ADMIN), adminRoute(INVENTORY_ADMIN)],
          withComponentInputBinding(),
        ),
      ],
    });
    harness = await RouterTestingHarness.create();
  });

  const page = () => harness.routeNativeElement!;
  const tabs = () => [...page().querySelectorAll<HTMLAnchorElement>('nav.tab-bar a')];
  const tab = (label: string) => tabs().find((link) => text(link) === label)!;
  const active = () =>
    tabs()
      .filter((link) => link.classList.contains('active'))
      .map(text);
  const current = () =>
    tabs()
      .filter((link) => link.getAttribute('aria-current') === 'page')
      .map(text);

  it('shows the heading, the description and the tabs of DevSecOps Admin', async () => {
    await harness.navigateByUrl('/admin/departments');

    expect(text(page().querySelector('h1'))).toBe('DevSecOps Admin');
    expect(text(page().querySelector('.page-header .page-description'))).toBe(
      'Set up the portal for everyone: departments, products and their services, what a new service gets, and the settings every pipeline shares.',
    );
    expect(page().querySelector('nav.tab-bar')?.getAttribute('aria-label')).toBe('DevSecOps Admin');
    expect(tabs().map(text)).toEqual([
      'Departments',
      'Products',
      'Service template',
      'Library defaults',
    ]);
    expect(tabs().map((link) => link.getAttribute('href'))).toEqual([
      '/admin/departments',
      '/admin/products',
      '/admin/template',
      '/admin/settings',
    ]);
    expect(text(page().querySelector('.tab-content'))).toBe('Departments');
    expect(page().querySelector('svg-icon')).toBeNull();
  });

  it.each([
    ['/admin/departments', 'Departments'],
    ['/admin/products', 'Products'],
    ['/admin/template', 'Service template'],
    ['/admin/settings', 'Library defaults'],
  ])('marks the tab of %s as the current page', async (url, label) => {
    await harness.navigateByUrl(url);

    expect(active()).toEqual([label]);
    expect(current()).toEqual([label]);
    expect(text(page().querySelector('.tab-content'))).toBe(label);
  });

  it('opens on the products tab', async () => {
    await harness.navigateByUrl('/admin');

    expect(TestBed.inject(Router).url).toBe('/admin/products');
    expect(active()).toEqual(['Products']);
  });

  it('moves the mark to the tab that is clicked', async () => {
    await harness.navigateByUrl('/admin/products');

    tab('Library defaults').click();
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/admin/settings');
    expect(active()).toEqual(['Library defaults']);
    expect(current()).toEqual(['Library defaults']);
    expect(text(page().querySelector('h1'))).toBe('DevSecOps Admin');
    expect(text(page().querySelector('.tab-content'))).toBe('Library defaults');
  });

  it('shows another admin area under its own path', async () => {
    await harness.navigateByUrl('/inventory/admin');

    expect(TestBed.inject(Router).url).toBe('/inventory/admin/products');
    expect(text(page().querySelector('h1'))).toBe('Inventory Admin');
    expect(text(page().querySelector('.page-description'))).toBe(
      'Departments and the products each one keeps in the inventory.',
    );
    expect(tabs().map(text)).toEqual(['Departments', 'Products']);
    expect(tabs().map((link) => link.getAttribute('href'))).toEqual([
      '/inventory/admin/departments',
      '/inventory/admin/products',
    ]);
    expect(active()).toEqual(['Products']);
  });
});
