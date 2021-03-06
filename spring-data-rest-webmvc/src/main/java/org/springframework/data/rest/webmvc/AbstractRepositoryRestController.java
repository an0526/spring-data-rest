/*
 * Copyright 2012-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.rest.webmvc;

import static org.springframework.data.rest.webmvc.ControllerUtils.*;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.springframework.data.auditing.AuditableBeanWrapper;
import org.springframework.data.auditing.AuditableBeanWrapperFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.rest.core.mapping.ResourceMetadata;
import org.springframework.data.rest.webmvc.support.ETag;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.ResourceSupport;
import org.springframework.hateoas.Resources;
import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * @author Jon Brisbin
 * @author Oliver Gierke
 * @author Thibaud Lepretre
 */
@SuppressWarnings({ "rawtypes" })
class AbstractRepositoryRestController {

	private final PagedResourcesAssembler<Object> pagedResourcesAssembler;
	private final AuditableBeanWrapperFactory auditableBeanWrapperFactory;

	/**
	 * Creates a new {@link AbstractRepositoryRestController} for the given {@link PagedResourcesAssembler} and
	 * {@link AuditableBeanWrapperFactory}.
	 * 
	 * @param pagedResourcesAssembler must not be {@literal null}.
	 * @param auditableBeanWrapperFactory must not be {@literal null}.
	 */
	public AbstractRepositoryRestController(PagedResourcesAssembler<Object> pagedResourcesAssembler,
			AuditableBeanWrapperFactory auditableBeanWrapperFactory) {

		Assert.notNull(pagedResourcesAssembler, "PagedResourcesAssembler must not be null!");
		Assert.notNull(auditableBeanWrapperFactory, "AuditableBeanWrapperFactory must not be null!");

		this.pagedResourcesAssembler = pagedResourcesAssembler;
		this.auditableBeanWrapperFactory = auditableBeanWrapperFactory;
	}

	protected Link resourceLink(RootResourceInformation resourceLink, Resource resource) {

		ResourceMetadata repoMapping = resourceLink.getResourceMetadata();

		Link selfLink = resource.getLink("self");
		String rel = repoMapping.getItemResourceRel();

		return new Link(selfLink.getHref(), rel);
	}

	@SuppressWarnings({ "unchecked" })
	protected Resources<?> toResources(Iterable<?> source, PersistentEntityResourceAssembler assembler, Link baseLink) {

		if (source instanceof Page) {
			Page<Object> page = (Page<Object>) source;
			return entitiesToResources(page, assembler, baseLink);
		} else if (source instanceof Iterable) {
			return entitiesToResources((Iterable<Object>) source, assembler);
		} else {
			return new Resources(EMPTY_RESOURCE_LIST);
		}
	}

	/**
	 * Turns the given source into a {@link ResourceSupport} if needed and possible. Uses the given
	 * {@link PersistentEntityResourceAssembler} for the actual conversion.
	 * 
	 * @param source can be must not be {@literal null}.
	 * @param assembler must not be {@literal null}.
	 * @param baseLink can be {@literal null}.
	 * @return
	 */
	protected Object toResource(Object source, PersistentEntityResourceAssembler assembler, Link baseLink) {

		if (source instanceof Iterable) {
			return toResources((Iterable<?>) source, assembler, baseLink);
		} else if (source == null || ClassUtils.isPrimitiveOrWrapper(source.getClass())) {
			return source;
		} else {
			return assembler.toFullResource(source);
		}
	}

	protected Resources<? extends Resource<Object>> entitiesToResources(Page<Object> page,
			PersistentEntityResourceAssembler assembler, Link baseLink) {
		return baseLink == null ? pagedResourcesAssembler.toResource(page, assembler) : pagedResourcesAssembler.toResource(
				page, assembler, baseLink);
	}

	protected Resources<Resource<Object>> entitiesToResources(Iterable<Object> entities,
			PersistentEntityResourceAssembler assembler) {

		List<Resource<Object>> resources = new ArrayList<Resource<Object>>();

		for (Object obj : entities) {
			resources.add(obj == null ? null : assembler.toResource(obj));
		}

		return new Resources<Resource<Object>>(resources);
	}

	/**
	 * Returns the default headers to be returned for the given {@link PersistentEntityResource}. Will set {@link ETag}
	 * and {@code Last-Modified} headers if applicable.
	 * 
	 * @param resource can be {@literal null}.
	 * @return
	 */
	protected HttpHeaders prepareHeaders(PersistentEntityResource resource) {

		HttpHeaders headers = new HttpHeaders();

		if (resource == null) {
			return headers;
		}

		// Add ETag
		headers = ETag.from(resource).addTo(headers);

		// Add Last-Modified
		AuditableBeanWrapper wrapper = getAuditableBeanWrapper(resource.getContent());

		if (wrapper == null) {
			return headers;
		}

		Calendar lastModifiedDate = wrapper.getLastModifiedDate();

		if (lastModifiedDate != null) {
			headers.setLastModified(lastModifiedDate.getTimeInMillis());
		}

		return headers;
	}

	/**
	 * Returns the {@link AuditableBeanWrapper} for the given source.
	 * 
	 * @param source can be {@literal null}.
	 * @return
	 */
	protected AuditableBeanWrapper getAuditableBeanWrapper(Object source) {
		return auditableBeanWrapperFactory.getBeanWrapperFor(source);
	}
}
