import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DocumentOcrReview } from './document-ocr-review';

describe('DocumentOcrReview', () => {
  let component: DocumentOcrReview;
  let fixture: ComponentFixture<DocumentOcrReview>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DocumentOcrReview],
    }).compileComponents();

    fixture = TestBed.createComponent(DocumentOcrReview);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
