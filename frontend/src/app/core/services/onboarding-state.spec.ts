import { TestBed } from '@angular/core/testing';

import { OnboardingState } from './onboarding-state';

describe('OnboardingState', () => {
  let service: OnboardingState;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(OnboardingState);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
