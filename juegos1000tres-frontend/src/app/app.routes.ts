import { Routes } from '@angular/router';

import { Auth } from './features/auth/auth';
import { Home } from './features/home/home';
import { Lobby } from './features/lobby/lobby';
import { Historial } from './features/historial/historial';
import { Login } from './features/auth/components/login/login';
import { Register } from './features/auth/components/register/register';
import { authGuard } from './core/guards/auth.guard';
import { registeredGuard } from './core/guards/registered.guard';
import { ReflejosP2PComponent } from './features/games/reflejos-p2p/reflejos-p2p.component';
import { PantallaReflejosComponent } from './features/games/reflejos-p2p/pantalla-reflejos.component';

export const routes: Routes = [
	{ path: '', component: Home },
	{
		path: 'login',
		component: Auth,
		children: [{ path: '', component: Login }],
	},
	{
		path: 'register',
		component: Auth,
		children: [{ path: '', component: Register }],
	},
	{ path: 'sala', component: Lobby, canActivate: [authGuard] },
	{ path: 'sala/:uuid', component: Lobby, canActivate: [authGuard] },
	{ path: 'historial', component: Historial, canActivate: [registeredGuard] },
	{ path: 'reflejos-p2p/:uuid', component: ReflejosP2PComponent, canActivate: [authGuard] },
	{ path: 'pantalla-reflejos/:uuid', component: PantallaReflejosComponent, canActivate: [authGuard] },
];
