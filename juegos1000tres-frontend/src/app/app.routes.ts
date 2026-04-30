import { Routes } from '@angular/router';

import { Auth } from './features/auth/auth';
import { Home } from './features/home/home';
import { Lobby } from './features/lobby/lobby';

export const routes: Routes = [
	{ path: '', component: Home },
	{ path: 'login', component: Auth },
	{ path: 'sala', component: Lobby },
	{ path: 'sala/:uuid', component: Lobby },
];
