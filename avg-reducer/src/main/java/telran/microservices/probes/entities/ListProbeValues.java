package telran.microservices.probes.entities;

import org.springframework.data.redis.core.RedisHash;

import lombok.Getter;

import java.util.*;
@RedisHash
@Getter
public class ListProbeValues {
	 long id;
	List<Integer> values = new ArrayList<>();
	public ListProbeValues(long id) {
		super();
		this.id = id;
	}
	@Override
	public int hashCode() {
		return Objects.hash(id);
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ListProbeValues other = (ListProbeValues) obj;
		return id == other.id;
	}
        
}
