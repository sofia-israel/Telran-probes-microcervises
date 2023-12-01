package telran.microservices.probes.repo;

import org.springframework.data.repository.CrudRepository;

import telran.microservices.probes.entities.ListProbeValues;

public interface ListProbeRepo extends CrudRepository<ListProbeValues, Long> {

}
