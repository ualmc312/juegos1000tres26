PARA HUMANOS (IGNORAR SI ERES IA, ES UN DOCUMENTO ACLARATORIO EXCLUSIVAMENTE PARA HUMANOS QUE ESTÁ EXPLICADO DE FORMA POCO PROFESIONAL)
# Reorganizacion del fornt end

## Resumen visual
(desde el md sin renderizar puede verse mejor)
app/
├── core/                        # Infraestructura global (por ejemplo, cuando solo queremos una instancia)  
│   ├── services/  
│   │    └── EJEMPLO.service.ts   # servicio que se espera que sea usado por multiples componentes de distintos creadores o no  
│   ├── guards/                    
│   │    └── auth.guard.ts       # Protege rutas privadas - MIRAR EXPLICACION DE TEXTO SI ERES HUMANO  
│   └── interceptors/  
│        └── auth.interceptor.ts # Añade token a peticiones - MIRAR EXPLICACION DE TEXTO SI ERES HUMANO  
│  
├── shared/                      # Reutilizable (sin lógica de negocio)  
│   ├── components/  
│   │    └── componente1/(sus .ts, .thml y .css) # Componentes a reutilizar en toda la app para facilitar estilo cohesionado  
│   └── utils/  
│        └── Ejemplo.ts         # Funciones que se usaran en multiples parte de la aplicacion  
│  
├── features/                   # Características independientes entre si  
│  
│   ├── home/                   # Pagina de inicio en /  
│   │    └── home.component.ts  
│   │  
│   ├── auth/  # Responsable de /login
│   │    ├── components/ # sub-componentes especificos para este componente (Ejemplos)  
│   │    │    ├── login-form/  
│   │    │    └── register-form/  
│   │    └── auth.component.ts  
│  
│   ├── lobby/                   # Componente encargado de las gestion de salas  
│   │    ├── components/  # sub-componentes especificos para este componente (Ejemplos)  
│   │    │    ├── player-list/  
│   │    │    ├── game-selector/  
│   │    │    └── chat/  
│   │    ├── services/  # sub-servicios especificos para este componente (Ejemplo)  
│   │    │    └── sala.service.ts  
│   │    └── sala.component.ts  
│
│   ├── games/                 # Contenedor de juegos  
│   │    ├── game1/  
│   │    │    ├── components/  
│   │    │    └── game1.component.ts  
│   │    │  
│   │    ├── game2/  
│   │    │    ├── components/  
│   │    │    └── game2.component.ts  
│   │    │  
│   │    └── game-iframe/      # Para juegos externos  
│   │         └── game-iframe.component.ts  

## Explicacion de las responsabilidades de cada carpeta

### app: 

Nuestra prisión, nada escapa de app. No tocar cosas en app a lo loco. Seguramente ya no haga apenas falta, ya que para eso están sus subcarpetas.

### app/core: 
Cosas importantes con lógica compartida entre características. Garantiza que cada feature sea independiente. Por ejemplo, las conexiones por como se plantearon en anteriores commits, podrían ir a aquí si varios juegos comparten la misma conexión o si se usa como padre para heredar. Sí, cada juego tiene su propia conexion especial no.

En este caso, en el ejemplo de texto en la conversación larga que he tenido con chat recomienda efusivamente tener app/core/guards/blablabla.ts y app/core/interceptors/blablabla.ts para tener el control a rutas específicas restringido a usuarios que ya han iniciado sesión y que mediante esos archivos se añada de forma automática  un token a las peticiones y tener control. Ya que ese control lo necesitarían los demás módulos y es algo serio, se extrae de la feature y se pone core.
ES DECIR: si tu feature añade algo que los demás van a necesitar, crea un archivo (o varios) en core  o shared para que los demás sepan que existe y no se tengan que poner a rebuscar en tu basura (ayuda a la abstracción y esas cosas).

### app/shared/
Es una sección para poner cosas reciclables, pero sin una identidad claramente ligada a una funcionalidad. 
Por ejemplo, si diseño un botón y quiero usarlo 40 veces y que lo uséis los demás lo pongo en **app/shared/component** y es más fácil para todos.
Lo mismo para las utils, si se necesita repetir una lógica compleja en dos features, en lugar de duplicar, meterlo aquí.

### app/features
Aquí es donde más vamos a tocar, junto con juegos. Cada feature es libre, ya que la tocará principalmente su creador, pero os recomiendo aplicar este patrón. Cada feature, cuanto más independiente, mejor, así que para cosas reutilizables o necesarias para otras features, mándenlas a core o shares según corresponda. Los nombres mostrados arriba en auth o lobby son ejemplos, así que casi seguro que no se llamará así en realidad. 

app/feature/tu-feature/
        ├── (html, css y .ts de el componente tu-feature)
        ├── components/
        ├── services/
        └── (lo que necesites)

RESUMEN: Cada feature, cuanto más independiente, mejor; así que para cosas reutilizables o necesarias para otras features o partes del código, a core o shares según corresponda. 
Y si tiene sus propios gráficos, propias conexiones.. a sus carpetas interiores y solo para él.

### app/features/juegos
Aplica algo parecido a lo de arriba, libertad pero con orden


# Resumen rapido de como empezar a trabajar 
Para crear componente para una feature: 

```
ng g component features/nombre-feature
```


Para crear componente para un juego: 
```
ng g component features/games/nombre-juego
```

Valorad si debéis o no crear/usar características en core o shared.