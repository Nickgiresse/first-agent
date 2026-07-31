import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AccountVerification } from './account-verification';

describe('AccountVerification', () => {
  let component: AccountVerification;
  let fixture: ComponentFixture<AccountVerification>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AccountVerification],
    }).compileComponents();

    fixture = TestBed.createComponent(AccountVerification);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
