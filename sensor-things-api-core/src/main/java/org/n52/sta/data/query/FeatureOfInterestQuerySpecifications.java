/*
 * Copyright (C) 2018-2019 52°North Initiative for Geospatial Open Source
 * Software GmbH
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 as published
 * by the Free Software Foundation.
 *
 * If the program is linked with libraries which are licensed under one of
 * the following licenses, the combination of the program with the linked
 * library is not considered a "derivative work" of the program:
 *
 *     - Apache License, version 2.0
 *     - Apache Software License, version 1.0
 *     - GNU Lesser General Public License, version 3
 *     - Mozilla Public License, versions 1.0, 1.1 and 2.0
 *     - Common Development and Distribution License (CDDL), version 1.0
 *
 * Therefore the distribution of the program linked with libraries licensed
 * under the aforementioned licenses, is permitted by the copyright holders
 * if the distribution is compliant with both the GNU General Public
 * License version 2 and the aforementioned licenses.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 */
package org.n52.sta.data.query;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

import org.apache.olingo.server.api.uri.queryoption.expression.BinaryOperatorKind;
import org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitException;
import org.n52.series.db.beans.AbstractFeatureEntity;
import org.n52.series.db.beans.DataEntity;
import org.n52.series.db.beans.DatasetEntity;
import org.n52.series.db.beans.DescribableEntity;
import org.springframework.data.jpa.domain.Specification;

/**
 * @author <a href="mailto:j.speckamp@52north.org">Jan Speckamp</a>
 *
 */
public class FeatureOfInterestQuerySpecifications extends EntityQuerySpecifications<AbstractFeatureEntity< ? >> {

    public Specification<AbstractFeatureEntity<?>> withObservation(Long observationId) {
        return (root, query, builder) -> {
            Subquery<Long> sqFeature = query.subquery(Long.class);
            Root<DatasetEntity> dataset = sqFeature.from(DatasetEntity.class);
            Subquery<DatasetEntity> sqDataset = query.subquery(DatasetEntity.class);
            Root<DataEntity> data = sqDataset.from(DataEntity.class);
            sqDataset.select(data.get(DataEntity.PROPERTY_DATASET))
                    .where(builder.equal(data.get(DescribableEntity.PROPERTY_ID), observationId));
            sqFeature.select(dataset.get(DatasetEntity.PROPERTY_FEATURE)).where(builder.in(dataset).value(sqDataset));
            return builder.in(root.get(AbstractFeatureEntity.PROPERTY_ID)).value(sqFeature);
        };
    }

    @Override
    public Specification<AbstractFeatureEntity<?>> withIdentifier(final String identifier) {
        return (root, query, builder) -> {
            return builder.equal(root.get(DescribableEntity.PROPERTY_ID), identifier);
        };
    }

    @Override
    public Specification<Long> getIdSubqueryWithFilter(Specification filter) {
        return this.toSubquery(AbstractFeatureEntity.class, AbstractFeatureEntity.PROPERTY_ID, filter);
    }

    @Override
    public Specification<AbstractFeatureEntity<?>> getFilterForProperty(String propertyName,
                                       Object propertyValue,
                                       BinaryOperatorKind operator,
                                       boolean switched)
            throws ExpressionVisitException {
        if (propertyName.equals("Observations")) {
            return handleRelatedPropertyFilter(propertyName, (Subquery<Long>) propertyValue, switched);
        } else if (propertyName.equals("id")) {
            return (root, query, builder) -> {
                try {
                    return handleDirectNumberPropertyFilter(root.<Long> get(AbstractFeatureEntity.PROPERTY_ID),
                            Long.getLong(propertyValue.toString()), operator, builder);
                } catch (ExpressionVisitException e) {
                    throw new RuntimeException(e);
                }
                //
            };
        } else {
            return handleDirectPropertyFilter(propertyName, propertyValue, operator, switched);
        }
    }

    private Specification<AbstractFeatureEntity<?>> handleRelatedPropertyFilter(String propertyName,
                                                          Subquery<Long> propertyValue,
                                                          boolean switched)
            throws ExpressionVisitException {
        return (root, query, builder) -> {
            // TODO ???
            Subquery<Long> sqFeature = query.subquery(Long.class);
            Root<DatasetEntity> dataset = sqFeature.from(DatasetEntity.class);
            Subquery<DatasetEntity> sqDataset = query.subquery(DatasetEntity.class);
            Root<DataEntity> data = sqDataset.from(DataEntity.class);
            sqDataset.select(data.get(DataEntity.PROPERTY_DATASET))
                    .where(builder.equal(data.get(DescribableEntity.PROPERTY_ID), propertyValue));
            sqFeature.select(dataset.get(DatasetEntity.PROPERTY_FEATURE)).where(builder.in(dataset).value(sqDataset));
            return builder.in(root.get(AbstractFeatureEntity.PROPERTY_ID)).value(sqFeature);
        };
    }

    private Specification<AbstractFeatureEntity<?>> handleDirectPropertyFilter(String propertyName, Object propertyValue,
            BinaryOperatorKind operator, boolean switched) {
        return new Specification<AbstractFeatureEntity<?>>() {
            @Override
            public Predicate toPredicate(Root<AbstractFeatureEntity<?>> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
                try {
                    switch (propertyName) {
                    case "name":
                        return handleDirectStringPropertyFilter(root.<String> get(DescribableEntity.PROPERTY_NAME),
                                propertyValue.toString(), operator, builder, switched);
                    case "description":
                        return handleDirectStringPropertyFilter(
                                root.<String> get(DescribableEntity.PROPERTY_DESCRIPTION), propertyValue.toString(), operator,
                                builder, switched);
                    case "encodingType":
                    case "featureType":
                        if (operator.equals(BinaryOperatorKind.EQ) && ("application/vnd.geo+json".equals(propertyValue) || "application/vnd.geo json".equals(propertyValue))) {
                            return builder.isNotNull(root.get(DescribableEntity.PROPERTY_ID));
                        }
                        return builder.isNull(root.get(DescribableEntity.PROPERTY_ID));
                    default:
                        throw new RuntimeException("Error getting filter for Property: \"" + propertyName
                                + "\". No such property in Entity.");
                    }
                } catch (ExpressionVisitException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}
