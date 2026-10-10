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

export const PIPELINES: PortalSection = {
  path: '/pipelines',
  label: 'Pipelines',
  heading: 'DevSecOps Pipelines',
  description:
    "Every automated build, test and security pipeline of your department's products. Open one to see its key, its Jenkinsfile and its latest runs.",
};

export const SELF_SERVICE: PortalSection = {
  path: '/self-service',
  label: 'Self-service',
  heading: 'DevSecOps Self-service',
  description:
    'Set up the DevSecOps pipelines of your product, or change them, in five guided steps. No DevSecOps knowledge needed.',
};

export const MONITORING: PortalSection = {
  path: '/monitoring',
  label: 'Pipeline Monitoring',
  heading: 'DevSecOps Pipeline Monitoring',
  description:
    'How the pipelines of every product are doing: whether their latest runs passed, and how often and how safely changes reach production.',
};

export const EVIDENCE: PortalSection = {
  path: '/evidence',
  label: 'Change Evidence',
  heading: 'DevSecOps Change Evidence',
  description:
    'Proof for a ProTech change: the builds, tests and security scans behind each pipeline of a product.',
};

export const ADMIN: PortalSection = {
  path: '/admin',
  label: 'Admin',
  heading: 'DevSecOps Admin',
  description:
    'Set up the portal for everyone: departments, products and their services, what a new service gets, and the settings every pipeline shares.',
};

export const SECTIONS: readonly PortalSection[] = [
  PIPELINES,
  SELF_SERVICE,
  MONITORING,
  EVIDENCE,
  ADMIN,
];

export const ADMIN_DEPARTMENTS: PortalTab = { path: '/admin/departments', label: 'Departments' };
export const ADMIN_PRODUCTS: PortalTab = { path: '/admin/products', label: 'Products' };
export const ADMIN_TEMPLATE: PortalTab = { path: '/admin/template', label: 'Service template' };
export const ADMIN_SETTINGS: PortalTab = { path: '/admin/settings', label: 'Library defaults' };

export const DEVSECOPS_ADMIN: AdminArea = {
  section: ADMIN,
  tabs: [ADMIN_DEPARTMENTS, ADMIN_PRODUCTS, ADMIN_TEMPLATE, ADMIN_SETTINGS],
};

export const CHANGES: PortalSection = {
  path: '/beadle/changes',
  label: 'Changes',
  heading: 'ProTech Changes',
  description:
    'The ProTech changes of your department and where each one is in its approval workflow. A change is read again from ProTech when you open it.',
};

export const NEW_CHANGE: PortalSection = {
  path: '/beadle/new-change',
  label: 'New Change',
  heading: 'New ProTech Change',
  description:
    "Raise a ProTech change for a production release in guided steps. The product's change template fills in the answers and Jira provides the scope.",
};

export const BEADLE_ADMIN: PortalSection = {
  path: '/beadle/admin',
  label: 'Admin',
  heading: 'Beadle Admin',
  description:
    "Departments, products and each product's change template: the answers every new change of the product starts with.",
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

export function pipelinePage(id: number | string): (string | number)[] {
  return [PIPELINES.path, id];
}

export function beadleProduct(id: number | string): (string | number)[] {
  return [BEADLE_PRODUCTS.path, id];
}

export function beadleChange(id: number | string, ...rest: string[]): (string | number)[] {
  return [CHANGES.path, id, ...rest];
}
