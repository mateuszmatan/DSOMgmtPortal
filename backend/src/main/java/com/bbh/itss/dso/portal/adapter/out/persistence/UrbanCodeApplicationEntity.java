package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.adapter.RecordMapper;
import com.bbh.itss.dso.portal.adapter.out.persistence.ServiceEntity.UrbanCodeComponentEmbeddable;
import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeApplicationSettings;
import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeComponent;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "DSO_UCD_APPLICATION")
public class UrbanCodeApplicationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "SERVICE_ID", nullable = false)
    private ServiceEntity service;

    @Column(name = "POSITION", nullable = false)
    private int position;

    @Column(name = "APPLICATION_NAME", nullable = false, length = 200)
    private String applicationName;

    @Column(name = "DEPLOY_ORDER")
    private Integer deployOrder;

    @Convert(converter = DelimitedListConverter.Commas.class)
    @Column(name = "ENVIRONMENTS", length = 500)
    private List<String> environments;

    @Column(name = "SNAPSHOT_NAME", length = 200)
    private String snapshotName;

    @ElementCollection
    @CollectionTable(name = "DSO_UCD_COMPONENT", joinColumns = @JoinColumn(name = "APPLICATION_ID"))
    @OrderColumn(name = "POSITION")
    private List<UrbanCodeComponentEmbeddable> components = new ArrayList<>();

    protected UrbanCodeApplicationEntity() {
    }

    UrbanCodeApplicationEntity(ServiceEntity service, int position, UrbanCodeApplicationSettings settings) {
        this.service = service;
        this.position = position;
        this.applicationName = settings.applicationName();
        this.deployOrder = settings.order();
        this.environments = settings.environments();
        this.snapshotName = settings.snapshotName();
        settings.components()
                .forEach(component -> components.add(RecordMapper.map(component, UrbanCodeComponentEmbeddable.class)));
    }

    UrbanCodeApplicationSettings toDomain() {
        return new UrbanCodeApplicationSettings(applicationName, deployOrder, environments, snapshotName,
                components.stream().map(component -> RecordMapper.map(component, UrbanCodeComponent.class)).toList());
    }
}
