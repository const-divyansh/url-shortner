/**
 * Business logic, exposed as a Facade over the collaborating components.
 *
 * <p>Pattern: Facade. The service layer is the single orchestration point so
 * controllers depend on one coherent API rather than on the generator,
 * repository, cache, and event publisher individually.
 *
 * <p>Depends on: {@code generator}, {@code repository}, {@code cache},
 * {@code event}, {@code validation}, {@code entity}, {@code dto}.
 */
package com.urlshortener.service;
