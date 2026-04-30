import { Component } from '@angular/core';
import { GenericButton } from '../../shared/components/generic-button/generic-button';

@Component({
  selector: 'app-home',
  imports: [GenericButton],
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class Home {}
