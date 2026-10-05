export interface PortalSection {
  path: string;
  label: string;
  heading: string;
  description: string;
}

export const PRODUCTS: PortalSection = {
  path: '/products',
  label: 'Product Management',
  heading: 'DevSecOps Product Management',
  description: 'Products, services, pipelines and keys',
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

export const SETTINGS: PortalSection = {
  path: '/settings',
  label: 'Global Settings',
  heading: 'DevSecOps Global Settings',
  description: 'Tools, policy and defaults of every pipeline',
};

export const SECTIONS: readonly PortalSection[] = [PRODUCTS, MONITORING, EVIDENCE, SETTINGS];
