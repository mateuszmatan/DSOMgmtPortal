package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.adapter.RecordMapper;
import com.bbh.itss.dso.portal.adapter.out.persistence.ServiceEntity.UrbanCodeApplicationEmbeddable;
import com.bbh.itss.dso.portal.adapter.out.persistence.ServiceEntity.UrbanCodeComponentEmbeddable;
import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeApplicationSettings;
import jakarta.persistence.CollectionTable;
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
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "SERVICE_ID")
    private ServiceEntity service;

    private int position;
    private UrbanCodeApplicationEmbeddable settings;

    @ElementCollection
    @CollectionTable(name = "DSO_UCD_COMPONENT", joinColumns = @JoinColumn(name = "APPLICATION_ID"))
    @OrderColumn(name = "POSITION")
    private List<UrbanCodeComponentEmbeddable> components = new ArrayList<>();

    protected UrbanCodeApplicationEntity() {
    }

    UrbanCodeApplicationEntity(ServiceEntity service, int position, UrbanCodeApplicationSettings settings) {
        this.service = service;
        this.position = position;
        this.settings = RecordMapper.map(settings, UrbanCodeApplicationEmbeddable.class);
        settings.components()
                .forEach(component -> components.add(RecordMapper.map(component, UrbanCodeComponentEmbeddable.class)));
    }

    UrbanCodeApplicationSettings toDomain() {
        return RecordMapper.map(UrbanCodeApplicationSettings.class, settings, this);
    }
}
