import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';

import { Taptap } from './taptap';

describe('Taptap', () => {
  let component: Taptap;
  let fixture: ComponentFixture<Taptap>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, Taptap],
    }).compileComponents();

    fixture = TestBed.createComponent(Taptap);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
