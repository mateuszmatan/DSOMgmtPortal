import { jenkinsBuildUrl } from './jenkins';

describe('jenkinsBuildUrl', () => {
  it('appends the build number to the job address', () => {
    expect(jenkinsBuildUrl('https://jenkins.bbh.com/job/DevSecOps/job/gui-full/', 42)).toBe(
      'https://jenkins.bbh.com/job/DevSecOps/job/gui-full/42/',
    );
  });

  it('adds the slash a job address without one lacks', () => {
    expect(jenkinsBuildUrl('https://jenkins.bbh.com/job/gui-full', 7)).toBe(
      'https://jenkins.bbh.com/job/gui-full/7/',
    );
  });

  it('links the first build too', () => {
    expect(jenkinsBuildUrl('https://jenkins.bbh.com/job/gui-full/', 0)).toBe(
      'https://jenkins.bbh.com/job/gui-full/0/',
    );
  });

  it('gives no link without a job address or a build number', () => {
    expect(jenkinsBuildUrl(null, 42)).toBeNull();
    expect(jenkinsBuildUrl('  ', 42)).toBeNull();
    expect(jenkinsBuildUrl('https://jenkins.bbh.com/job/gui-full/', null)).toBeNull();
    expect(jenkinsBuildUrl(undefined, undefined)).toBeNull();
  });
});
