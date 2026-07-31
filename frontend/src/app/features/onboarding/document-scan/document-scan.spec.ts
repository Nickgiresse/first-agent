import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DocumentScan } from './document-scan';

describe('DocumentScan', () => {
  let component: DocumentScan;
  let fixture: ComponentFixture<DocumentScan>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DocumentScan],
    }).compileComponents();

    fixture = TestBed.createComponent(DocumentScan);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('starts on the front-capture step', () => {
    expect(component.step()).toBe('FRONT');
  });
});
