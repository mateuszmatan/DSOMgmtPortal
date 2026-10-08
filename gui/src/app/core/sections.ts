export interface PortalSection {
  path: string;
  label: string;
  heading: string;
  description: string;
}

export interface PortalTab {
  path: string;
  label: string;
}

export interface AdminArea {
  section: PortalSection;
  tabs: readonly PortalTab[];
}

export const SELF_SERVICE: PortalSection = {
  path: '/self-service',
  label: 'Self-service',
  heading: 'DevSecOps Self-service',
  description: 'Set up or change the DevSecOps pipelines of your product, step by step',
};

export const MONITORING: PortalSection = {
  path: '/monitoring',
  label: 'Pipeline Monitoring',
  heading: 'DevSecOps Pipeline Monitoring',
  description: 'Pipeline status and DORA metrics',
};

export const EVIDENCE: PortalSection = {
  path: '/evidence',
  label: 'Change Evidence',
  heading: 'DevSecOps Change Evidence',
  description: 'Builds, tests and scans for ServiceNow changes',
};

export const ADMIN: PortalSection = {
  path: '/admin',
  label: 'Admin',
  heading: 'DevSecOps Admin',
  description: 'Departments, products, services and the DSOEnhanced library defaults',
};

export const SECTIONS: readonly PortalSection[] = [SELF_SERVICE, MONITORING, EVIDENCE, ADMIN];

export const ADMIN_DEPARTMENTS: PortalTab = { path: '/admin/departments', label: 'Departments' };
export const ADMIN_PRODUCTS: PortalTab = { path: '/admin/products', label: 'Products' };
export const ADMIN_SETTINGS: PortalTab = { path: '/admin/settings', label: 'Library defaults' };

export const DEVSECOPS_ADMIN: AdminArea = {
  section: ADMIN,
  tabs: [ADMIN_DEPARTMENTS, ADMIN_PRODUCTS, ADMIN_SETTINGS],
};

export const CHANGES: PortalSection = {
  path: '/beadle/changes',
  label: 'Changes',
  heading: 'ProTech Changes',
  description: 'The ProTech changes of your department, read from ProTech each time you open them',
};

export const NEW_CHANGE: PortalSection = {
  path: '/beadle/new-change',
  label: 'New Change',
  heading: 'New ProTech Change',
  description: 'Raise a ProTech change (CHG) with its change tasks (CTASK), written from Jira',
};

export const BEADLE_ADMIN: PortalSection = {
  path: '/beadle/admin',
  label: 'Admin',
  heading: 'Beadle Admin',
  description: 'Departments, products and the change template of each product',
};

export const BEADLE_DEPARTMENTS: PortalTab = {
  path: '/beadle/admin/departments',
  label: 'Departments',
};
export const BEADLE_PRODUCTS: PortalTab = { path: '/beadle/admin/products', label: 'Products' };

export const BEADLE_ADMINISTRATION: AdminArea = {
  section: BEADLE_ADMIN,
  tabs: [BEADLE_DEPARTMENTS, BEADLE_PRODUCTS],
};

export interface PortalMenu {
  label: string;
  sections: readonly PortalSection[];
}

export const MENUS: readonly PortalMenu[] = [
  { label: 'Beadle', sections: [CHANGES, NEW_CHANGE, BEADLE_ADMIN] },
  { label: 'DevSecOps Management', sections: SECTIONS },
];

export function adminProduct(id: number | string, ...rest: string[]): (string | number)[] {
  return [ADMIN_PRODUCTS.path, id, ...rest];
}

export function beadleProduct(id: number | string): (string | number)[] {
  return [BEADLE_PRODUCTS.path, id];
}

export function beadleChange(id: number | string, ...rest: string[]): (string | number)[] {
  return [CHANGES.path, id, ...rest];
}
