import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PinCreation } from './pin-creation';

describe('PinCreation', () => {
  let component: PinCreation;
  let fixture: ComponentFixture<PinCreation>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PinCreation],
    }).compileComponents();

    fixture = TestBed.createComponent(PinCreation);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
