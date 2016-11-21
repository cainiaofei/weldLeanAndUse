/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016, Red Hat, Inc., and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.weld.environment.se;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.enterprise.event.Reception;
import javax.enterprise.event.TransactionPhase;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.AfterTypeDiscovery;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.BeforeShutdown;
import javax.enterprise.inject.spi.ObserverMethod;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessBean;
import javax.enterprise.inject.spi.ProcessBeanAttributes;
import javax.enterprise.inject.spi.ProcessInjectionPoint;
import javax.enterprise.inject.spi.ProcessInjectionTarget;
import javax.enterprise.inject.spi.ProcessManagedBean;
import javax.enterprise.inject.spi.ProcessObserverMethod;
import javax.enterprise.inject.spi.ProcessProducer;
import javax.enterprise.inject.spi.ProcessProducerField;
import javax.enterprise.inject.spi.ProcessProducerMethod;
import javax.enterprise.inject.spi.ProcessSessionBean;
import javax.enterprise.inject.spi.ProcessSyntheticAnnotatedType;
import javax.enterprise.inject.spi.WithAnnotations;
import javax.enterprise.util.TypeLiteral;

import org.jboss.weld.bootstrap.SyntheticExtension;
import org.jboss.weld.bootstrap.events.NotificationListener;
import org.jboss.weld.environment.se.logging.WeldSELogger;
import org.jboss.weld.event.ContainerLifecycleEventObserverMethod;
import org.jboss.weld.util.Observers;
import org.jboss.weld.util.Preconditions;
import org.jboss.weld.util.reflection.Reflections;

/**
 * A synthetic container lifecycle event observer.
 *
 * @author Martin Kouba
 * @see SyntheticExtension
 * @see Weld#addExtension(javax.enterprise.inject.spi.Extension)
 * @see Weld#addContainerLifecycleObserver(ContainerLifecycleObserver)
 */
public final class ContainerLifecycleObserver<T> implements ContainerLifecycleEventObserverMethod<T> {

    private final int priority;

    private final Type observedType;

    private final BiConsumer<T, BeanManager> callbackWithBeanManager;

    private final Consumer<T> callback;

    private final Collection<Class<? extends Annotation>> requiredAnnotations;

    private volatile BeanManager beanManager;

    private volatile SyntheticExtension extension;

    /**
     *
     * @param priority
     * @param observedType
     * @param callbackWithBeanManager
     * @param callback
     * @param requiredAnnotations
     */
    private ContainerLifecycleObserver(int priority, Type observedType, BiConsumer<T, BeanManager> callbackWithBeanManager, Consumer<T> callback,
            Collection<Class<? extends Annotation>> requiredAnnotations) {
        this.priority = priority;
        this.observedType = observedType;
        this.callbackWithBeanManager = callbackWithBeanManager;
        this.callback = callback;
        this.requiredAnnotations = requiredAnnotations;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public Class<?> getBeanClass() {
        return ContainerLifecycleObserver.class;
    }

    @Override
    public Type getObservedType() {
        return observedType;
    }

    @Override
    public Set<Annotation> getObservedQualifiers() {
        return Collections.emptySet();
    }

    @Override
    public Reception getReception() {
        return Reception.ALWAYS;
    }

    @Override
    public TransactionPhase getTransactionPhase() {
        return TransactionPhase.IN_PROGRESS;
    }

    @Override
    public void notify(T event) {
        if (beanManager == null || extension == null) {
            throw WeldSELogger.LOG.containerLifecycleObserverNotInitialized(toString());
        }
        if (event instanceof NotificationListener) {
            NotificationListener.class.cast(event).preNotify(extension);
        }
        try {
            if (callbackWithBeanManager != null) {
                callbackWithBeanManager.accept(event, beanManager);
            } else {
                callback.accept(event);
            }
        } finally {
            if (event instanceof NotificationListener) {
                NotificationListener.class.cast(event).postNotify(null);
            }
        }
    }

    @Override
    public Collection<Class<? extends Annotation>> getRequiredAnnotations() {
        return requiredAnnotations;
    }

    private void setBeanManager(BeanManager beanManager) {
        this.beanManager = beanManager;
    }

    private void setExtension(SyntheticExtension extension) {
        this.extension = extension;
    }

    @Override
    public String toString() {
        return String.format("ContainerLifecyleObserver [priority=%s, observedType=%s]", priority, observedType);
    }

    // Static methods used to init a new builder or a new observer instance

    /**
     *
     * @return a new builder instance
     * @see BeforeBeanDiscovery
     */
    public static Builder<BeforeBeanDiscovery> beforeBeanDiscovery() {
        return ContainerLifecycleObserver.<BeforeBeanDiscovery> of(BeforeBeanDiscovery.class);
    }

    /**
     *
     * @param callback
     * @return a new container lifecycle observer
     * @see BeforeBeanDiscovery
     */
    public static ContainerLifecycleObserver<BeforeBeanDiscovery> beforeBeanDiscovery(Consumer<BeforeBeanDiscovery> callback) {
        return beforeBeanDiscovery().notify(callback);
    }

    /**
     *
     * @return a new builder instance
     * @see AfterBeanDiscovery
     */
    public static Builder<AfterBeanDiscovery> afterBeanDiscovery() {
        return ContainerLifecycleObserver.<AfterBeanDiscovery> of(AfterBeanDiscovery.class);
    }

    /**
     *
     * @param callback
     * @return a new container lifecycle observer
     * @see AfterBeanDiscovery
     */
    public static ContainerLifecycleObserver<AfterBeanDiscovery> afterBeanDiscovery(Consumer<AfterBeanDiscovery> callback) {
        return afterBeanDiscovery().notify(callback);
    }

    /**
     *
     * @return a new builder instance
     * @see AfterTypeDiscovery
     */
    public static Builder<AfterTypeDiscovery> afterTypeDiscovery() {
        return ContainerLifecycleObserver.<AfterTypeDiscovery> of(AfterTypeDiscovery.class);
    }

    /**
     *
     * @param callback
     * @return a new container lifecycle observer
     * @see AfterTypeDiscovery
     */
    public static ContainerLifecycleObserver<AfterTypeDiscovery> afterTypeDiscovery(Consumer<AfterTypeDiscovery> callback) {
        return afterTypeDiscovery().notify(callback);
    }

    /**
     *
     * @param callback
     * @return a new builder instance
     * @see AfterDeploymentValidation
     */
    public static Builder<AfterDeploymentValidation> afterDeploymentValidation() {
        return ContainerLifecycleObserver.<AfterDeploymentValidation> of(AfterDeploymentValidation.class);
    }

    /**
     *
     * @param callback
     * @return a new container lifecycle observer
     * @see AfterDeploymentValidation
     */
    public static ContainerLifecycleObserver<AfterDeploymentValidation> afterDeploymentValidation(Consumer<AfterDeploymentValidation> callback) {
        return afterDeploymentValidation().notify(callback);
    }

    /**
     *
     * @param callback
     * @return a new builder instance
     * @see BeforeShutdown
     */
    public static Builder<BeforeShutdown> beforeShutdown() {
        return ContainerLifecycleObserver.<BeforeShutdown> of(BeforeShutdown.class);
    }

    /**
     *
     * @param callback
     * @return a new container lifecycle observer
     * @see BeforeShutdown
     */
    public static ContainerLifecycleObserver<BeforeShutdown> beforeShutdown(Consumer<BeforeShutdown> callback) {
        return beforeShutdown().notify(callback);
    }

    /**
     *
     * @return a new builder instance
     * @see ProcessAnnotatedType
     */
    @SuppressWarnings("serial")
    public static Builder<ProcessAnnotatedType<?>> processAnnotatedType() {
        return processAnnotatedType(new TypeLiteral<ProcessAnnotatedType<?>>() {
        }.getType());
    }

    /**
     *
     * @param observedType
     * @return a new builder instance
     * @see ProcessAnnotatedType
     */
    public static Builder<ProcessAnnotatedType<?>> processAnnotatedType(Type observedType) {
        checkRawType(observedType, ProcessAnnotatedType.class);
        return ContainerLifecycleObserver.<ProcessAnnotatedType<?>> of(observedType);
    }

    /**
     *
     * @param observedType
     * @return a new builder instance
     * @see ProcessSyntheticAnnotatedType
     */
    public static Builder<ProcessSyntheticAnnotatedType<?>> processSyntheticAnnotatedType(Type observedType) {
        checkRawType(observedType, ProcessSyntheticAnnotatedType.class);
        return ContainerLifecycleObserver.<ProcessSyntheticAnnotatedType<?>> of(observedType);
    }

    /**
     *
     * @return a new builder instance
     * @see ProcessInjectionPoint
     */
    @SuppressWarnings("serial")
    public static Builder<ProcessInjectionPoint<?, ?>> processInjectionPoint() {
        return processInjectionPoint(new TypeLiteral<ProcessInjectionPoint<?, ?>>() {
        }.getType());
    }

    /**
     *
     * @param observedType
     * @return a new builder instance
     * @see ProcessInjectionPoint
     */
    public static Builder<ProcessInjectionPoint<?, ?>> processInjectionPoint(Type observedType) {
        checkRawType(observedType, ProcessInjectionPoint.class);
        return ContainerLifecycleObserver.<ProcessInjectionPoint<?, ?>> of(observedType);
    }

    /**
     *
     * @return a new builder instance
     * @see ProcessInjectionTarget
     *
     */
    @SuppressWarnings("serial")
    public static Builder<ProcessInjectionTarget<?>> processInjectionTarget() {
        return processInjectionTarget(new TypeLiteral<ProcessInjectionTarget<?>>() {
        }.getType());
    }

    /**
     *
     * @param observedType
     * @return a new builder instance
     * @see ProcessInjectionTarget
     */
    public static Builder<ProcessInjectionTarget<?>> processInjectionTarget(Type observedType) {
        checkRawType(observedType, ProcessInjectionTarget.class);
        return ContainerLifecycleObserver.<ProcessInjectionTarget<?>> of(observedType);
    }

    /**
     *
     * @return a new builder instance
     * @see ProcessBeanAttributes
     */
    @SuppressWarnings("serial")
    public static Builder<ProcessBeanAttributes<?>> processBeanAttributes() {
        return processBeanAttributes(new TypeLiteral<ProcessBeanAttributes<?>>() {
        }.getType());
    }

    /**
     *
     * @param observedType
     * @return a new builder instance
     * @see ProcessBeanAttributes
     */
    public static Builder<ProcessBeanAttributes<?>> processBeanAttributes(Type observedType) {
        checkRawType(observedType, ProcessBeanAttributes.class);
        return ContainerLifecycleObserver.<ProcessBeanAttributes<?>> of(observedType);
    }

    /**
     *
     * @return a new builder instance
     * @see ProcessBean
     */
    @SuppressWarnings("serial")
    public static Builder<ProcessBean<?>> processBean() {
        return processBean(new TypeLiteral<ProcessBean<?>>() {
        }.getType());
    }

    /**
     *
     * @param observedType
     * @return a new builder instance
     * @see ProcessBean
     */
    public static Builder<ProcessBean<?>> processBean(Type observedType) {
        checkRawType(observedType, ProcessBean.class);
        return ContainerLifecycleObserver.<ProcessBean<?>> of(observedType);
    }

    /**
     *
     * @return a new builder instance
     * @see ProcessManagedBean
     */
    @SuppressWarnings("serial")
    public static Builder<ProcessManagedBean<?>> processManagedBean() {
        return processManagedBean(new TypeLiteral<ProcessManagedBean<?>>() {
        }.getType());
    }

    /**
     *
     * @param observedType
     * @return a new builder instance
     * @see ProcessManagedBean
     */
    public static Builder<ProcessManagedBean<?>> processManagedBean(Type observedType) {
        checkRawType(observedType, ProcessManagedBean.class);
        return ContainerLifecycleObserver.<ProcessManagedBean<?>> of(observedType);
    }

    /**
     *
     * @return a new builder instance
     * @see ProcessSessionBean
     */
    @SuppressWarnings("serial")
    public static Builder<ProcessSessionBean<?>> processSessionBean() {
        return processSessionBean(new TypeLiteral<ProcessSessionBean<?>>() {
        }.getType());
    }

    /**
     *
     * @param observedType
     * @return a new builder instance
     * @see ProcessSessionBean
     */
    public static Builder<ProcessSessionBean<?>> processSessionBean(Type observedType) {
        checkRawType(observedType, ProcessSessionBean.class);
        return ContainerLifecycleObserver.<ProcessSessionBean<?>> of(observedType);
    }

    /**
     *
     * @return a new builder instance
     * @see ProcessProducerMethod
     */
    @SuppressWarnings("serial")
    public static Builder<ProcessProducerMethod<?, ?>> processProducerMethod() {
        return processProducerMethod(new TypeLiteral<ProcessProducerMethod<?, ?>>() {
        }.getType());
    }

    /**
     *
     * @param observedType
     * @return a new builder instance
     * @see ProcessProducerMethod
     */
    public static Builder<ProcessProducerMethod<?, ?>> processProducerMethod(Type observedType) {
        checkRawType(observedType, ProcessProducerMethod.class);
        return ContainerLifecycleObserver.<ProcessProducerMethod<?, ?>> of(observedType);
    }

    /**
     *
     * @return a new builder instance
     * @see ProcessProducerField
     */
    @SuppressWarnings("serial")
    public static Builder<ProcessProducerField<?, ?>> processProducerField() {
        return processProducerField(new TypeLiteral<ProcessProducerField<?, ?>>() {
        }.getType());
    }

    /**
     *
     * @param observedType
     * @return a new builder instance
     * @see ProcessProducerField
     */
    public static Builder<ProcessProducerField<?, ?>> processProducerField(Type observedType) {
        checkRawType(observedType, ProcessProducerField.class);
        return ContainerLifecycleObserver.<ProcessProducerField<?, ?>> of(observedType);
    }

    /**
     *
     * @return a new builder instance
     * @see ProcessProducer
     */
    @SuppressWarnings("serial")
    public static Builder<ProcessProducer<?, ?>> processProducer() {
        return processProducer(new TypeLiteral<ProcessProducer<?, ?>>() {
        }.getType());
    }

    /**
     *
     * @param observedType
     * @return a new builder instance
     * @see ProcessProducer
     */
    public static Builder<ProcessProducer<?, ?>> processProducer(Type observedType) {
        checkRawType(observedType, ProcessProducer.class);
        return ContainerLifecycleObserver.<ProcessProducer<?, ?>> of(observedType);
    }

    /**
     *
     * @return a new builder instance
     * @see ProcessObserverMethod
     */
    @SuppressWarnings("serial")
    public static Builder<ProcessObserverMethod<?, ?>> processObserverMethod() {
        return processObserverMethod(new TypeLiteral<ProcessObserverMethod<?, ?>>() {
        }.getType());
    }

    /**
     *
     * @param observedType
     * @return a new builder instance
     * @see ProcessObserverMethod
     */
    public static Builder<ProcessObserverMethod<?, ?>> processObserverMethod(Type observedType) {
        checkRawType(observedType, ProcessObserverMethod.class);
        return ContainerLifecycleObserver.<ProcessObserverMethod<?, ?>> of(observedType);
    }

    /**
     * A synthetic extension is basically a container for synthetic container lifecycle event observers.
     *
     * @return a builder for a synthetic extension
     */
    public static ContainerLifecycleObserverExtension.Builder extensionBuilder() {
        return new ContainerLifecycleObserverExtension.Builder();
    }

    private static <T> Builder<T> of(Type observedType) {
        if (!Observers.CONTAINER_LIFECYCLE_EVENT_TYPES.contains(Reflections.getRawType(observedType))) {
            throw WeldSELogger.LOG.observedTypeNotContonainerLifecycleEventType(observedType);
        }
        return new Builder<>(observedType);
    }

    private static void checkRawType(Type observedType, Class<?> rawType) {
        if (!rawType.equals(Reflections.getRawType(observedType))) {
            throw WeldSELogger.LOG.observedTypeDoesNotMatchContonainerLifecycleEventType(observedType, rawType);
        }
    }

    public static class Builder<T> {

        private static final String OBSERVED_TYPE = "observedType";
        private static final String CALLBACK = "callback";

        @SuppressWarnings("checkstyle:magicnumber")
        private static final int DEFAULT_PRIORITY = javax.interceptor.Interceptor.Priority.APPLICATION + 500;

        private int priority = DEFAULT_PRIORITY;

        private Type observedType;

        private Collection<Class<? extends Annotation>> requiredAnnotations = Collections.emptySet();

        private Builder(Type observedType) {
            Preconditions.checkArgumentNotNull(observedType, OBSERVED_TYPE);
            this.observedType = observedType;
        }

        /**
         * Set the priority.
         *
         * @param priority
         * @return self
         */
        public Builder<T> priority(int priority) {
            this.priority = priority;
            return this;
        }

        /**
         * The annotations are only considered for {@link ProcessAnnotatedType}, i.e. they are ignored for other container lifecycle events.
         *
         * @param annotations
         * @return self
         * @see WithAnnotations
         */
        @SafeVarargs
        public final Builder<T> withAnnotations(Class<? extends Annotation>... annotations) {
            this.requiredAnnotations = new HashSet<>();
            Collections.addAll(requiredAnnotations, annotations);
            return this;
        }

        /**
         * Set a callback used during observer notification. The first callback parameter is an event object and the second parameter is a {@link BeanManager}
         * instance.
         * <p>
         * This is a terminal operation.
         *
         * @param callback
         * @return the built observer
         * @see ObserverMethod#notify(Object)
         */
        public ContainerLifecycleObserver<T> notify(BiConsumer<T, BeanManager> callback) {
            Preconditions.checkArgumentNotNull(callback, CALLBACK);
            return new ContainerLifecycleObserver<>(priority, observedType, callback, null, requiredAnnotations);
        }

        /**
         * Set a callback used during observer notification. The callback parameter is an event object.
         * <p>
         * This is a terminal operation.
         *
         * @param callback
         * @return the built observer
         * @see ObserverMethod#notify(Object)
         */
        public ContainerLifecycleObserver<T> notify(Consumer<T> callback) {
            Preconditions.checkArgumentNotNull(callback, CALLBACK);
            return new ContainerLifecycleObserver<>(priority, observedType, null, callback, requiredAnnotations);
        }

    }

    /**
     * A synthetic extension, i.e. a collection of synthetic container lifecycle observers.
     *
     * @author Martin Kouba
     */
    public static class ContainerLifecycleObserverExtension implements SyntheticExtension {

        private final List<ContainerLifecycleObserver<?>> observers;

        ContainerLifecycleObserverExtension(List<ContainerLifecycleObserver<?>> observers) {
            this.observers = new ArrayList<>(observers);
        }

        public void initialize(BeanManager beanManager) {
            for (ContainerLifecycleObserver<?> observer : observers) {
                observer.setBeanManager(beanManager);
                observer.setExtension(this);
            }
        }

        @Override
        public Collection<ContainerLifecycleEventObserverMethod<?>> getObservers() {
            return Reflections.cast(observers);
        }

        public static class Builder {

            private final List<ContainerLifecycleObserver<?>> observers;

            private Builder() {
                this.observers = new ArrayList<>();
            }

            /**
             *
             * @param observer
             * @return self
             */
            public ContainerLifecycleObserverExtension.Builder add(ContainerLifecycleObserver<?> observer) {
                observers.add(observer);
                return this;
            }

            public ContainerLifecycleObserverExtension build() {
                return new ContainerLifecycleObserverExtension(observers);
            }

        }

    }

}