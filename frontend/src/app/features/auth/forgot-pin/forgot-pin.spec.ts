import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ForgotPin } from './forgot-pin';

describe('ForgotPin', () => {
  let component: ForgotPin;
  let fixture: ComponentFixture<ForgotPin>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ForgotPin],
    }).compileComponents();

    fixture = TestBed.createComponent(ForgotPin);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
