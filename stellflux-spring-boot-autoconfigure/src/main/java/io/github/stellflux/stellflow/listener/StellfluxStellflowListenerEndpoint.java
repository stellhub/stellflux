package io.github.stellflux.stellflow.listener;

import java.lang.reflect.Method;

record StellfluxStellflowListenerEndpoint(Object bean, Method method, StellflowListener listener) {}
