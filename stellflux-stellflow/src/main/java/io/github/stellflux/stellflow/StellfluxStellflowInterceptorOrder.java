package io.github.stellflux.stellflow;

/** Stellflow 拦截器顺序常量。 */
public final class StellfluxStellflowInterceptorOrder {

    public static final int FRAMEWORK = 0;
    public static final int OBSERVABILITY = 100;
    public static final int USER = 1000;

    private StellfluxStellflowInterceptorOrder() {}
}
