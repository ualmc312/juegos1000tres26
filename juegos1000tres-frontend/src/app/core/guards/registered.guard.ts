import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from '../../features/auth/services/auth.service';

export const registeredGuard: CanActivateFn = () => {
	const auth = inject(AuthService);
	const router = inject(Router);

	return auth.loadSession().pipe(
		map(user => {
			if (user && user.role === 'USER') {
				return true;
			}
			router.navigate(['/']);
			return false;
		})
	);
};