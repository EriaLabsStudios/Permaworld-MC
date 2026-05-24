# Fabric Mixins Mini

- `@Mixin(Clase.class)`: declara que esta clase se inyecta o conecta contra `Clase`.
- `@Shadow`: espejo de un campo o metodo que ya existe en la clase objetivo; no lo crea, solo permite usarlo desde el mixin.
- `@Invoker`: puente para llamar a un metodo existente de la clase objetivo, util con metodos protegidos/privados.
- `@Inject`: mete codigo propio en un punto concreto de un metodo existente.
- `CallbackInfo`: objeto de control de una inyeccion; si es cancellable, permite cancelar el metodo original.
