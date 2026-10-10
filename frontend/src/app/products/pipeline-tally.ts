import { counted } from '../shared/formatting';

export function invalidatedKeys(pipelines: number, active: number): string | null {
  const invalidated = pipelines - active;
  return invalidated > 0 ? `${counted(invalidated, 'key')} invalidated` : null;
}

export function pipelineTally(pipelines: number, active: number): string {
  if (pipelines === 0) {
    return 'None yet';
  }
  const invalidated = invalidatedKeys(pipelines, active);
  return `${pipelines}, ${invalidated ?? (pipelines === 1 ? 'active' : 'all active')}`;
}
