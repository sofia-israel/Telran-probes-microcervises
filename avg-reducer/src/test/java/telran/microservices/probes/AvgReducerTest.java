package telran.microservices.probes;

import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;

import telran.microservices.probes.dto.Probe;
import telran.microservices.probes.entities.ListProbeValues;
import telran.microservices.probes.repo.ListProbeRepo;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
//Из-за @Import она будет искать два бина - знает, что должны быть input и output
@Import(TestChannelBinderConfiguration.class) // это мы импортируем аппликационный контекст - Не кафка, не карафка, а
												// вот такое
// это будет mock - имитатор
public class AvgReducerTest {
	private static final long PROBE_ID_NO_AVG = 123;
	private static final long PROBE_ID_AVG = 124;
	private static final long PROBE_ID_NO_VALUES = 125;
	private static final int VALUE = 100; // пусть оба значения будут 100 и среднее тогда тоже будет 100
	@Autowired
	InputDestination producer; // он будет посылать. Ведь мы проверяем консьюмера - это наш имитатор
	@Autowired
	OutputDestination consumer; // это уже наш output, который мы будем проверять
	@MockBean
	ListProbeRepo listProbeRepo;
	
	static List<Integer> valuesNoAvg;
	static List<Integer> valuesAvg; // посчитается это, то на основе чего мы это посчитали стирается, и это значение отправляется наружу
	
	static ListProbeValues listProbeNoAvg = new ListProbeValues(PROBE_ID_NO_AVG);
	static ListProbeValues listProbeAvg = new ListProbeValues(PROBE_ID_AVG);
	
	static HashMap<Long, ListProbeValues> redisMap = new HashMap<>();
	Probe probeNoValues = new Probe(PROBE_ID_NO_VALUES, VALUE);
	Probe probeNoAvg = new Probe(PROBE_ID_NO_AVG, VALUE);
	// в Redis был бы put, а тут должен произойти save
	Probe probeAvg = new Probe(PROBE_ID_AVG, VALUE);
	//логические имена каналов для Spring Cloud
	private String producerBindingName = "avgProducer-out-0";
	private String consumerBindingName = "avgConsumer-in-0";

	
	@BeforeAll // аннотация для установки исходного положения
	static void setUpAll() {
		valuesNoAvg = listProbeNoAvg.getValues();
		valuesAvg = listProbeAvg.getValues();
		valuesAvg.add(VALUE);
		redisMap.put(PROBE_ID_AVG, listProbeAvg);
		redisMap.put(PROBE_ID_NO_AVG, listProbeNoAvg);
	}
	
	@Test
	void probeNoValuesTest() {
		// настраиваем наш Мокито
		when(listProbeRepo.findById(PROBE_ID_NO_VALUES))
		.thenReturn(Optional.ofNullable(null));
		when(listProbeRepo.save(new ListProbeValues(PROBE_ID_NO_VALUES)))
	        .thenAnswer(new Answer<ListProbeValues>() {

			@Override
			public ListProbeValues answer(InvocationOnMock invocation) throws Throwable {
				redisMap.put(PROBE_ID_NO_VALUES, invocation.getArgument(0));
				return invocation.getArgument(0);
			}
		});
		producer.send(new GenericMessage<Probe>(probeNoValues), consumerBindingName);
		
		// тут мы начинаем проверять
		// здесь 100 это timeout
		Message<byte[]> message = consumer.receive(100, producerBindingName ); // чтобы проверить что вернет
		assertNull(message); // среднего значения еще нет и мы должны получить null
		assertEquals(VALUE, redisMap.get(PROBE_ID_NO_VALUES).getValues().get(0)); // а значение в маp появилось
	}
	
	@Test
	void probeNoAvgTest() {
		when(listProbeRepo.findById(PROBE_ID_NO_AVG))
		.thenReturn(Optional.of(listProbeNoAvg));
		when(listProbeRepo.save(new ListProbeValues(PROBE_ID_NO_AVG)))
	        .thenAnswer(new Answer<ListProbeValues>() {

			@Override
			public ListProbeValues answer(InvocationOnMock invocation) throws Throwable {
				redisMap.put(PROBE_ID_NO_AVG, invocation.getArgument(0));
				return invocation.getArgument(0);
			}
		});
		producer.send(new GenericMessage<Probe>(probeNoValues), consumerBindingName);
		
		// приходит нам опять null, потому что мы еще ничего не отправили
		Message<byte[]> message = consumer.receive(100, producerBindingName ); // чтобы проверить что вернет
		assertNull(message); // среднего значения еще нет и мы должны получить null
		// здесь у нас уже два элемента
		assertEquals(VALUE, redisMap.get(PROBE_ID_NO_AVG).getValues().get(0)); // а значение в маp появилось
	}
	
	@Test
	void probeAvgTest() throws Exception {
		when(listProbeRepo.findById(PROBE_ID_AVG))
		.thenReturn(Optional.of(listProbeAvg));
		when(listProbeRepo.save(new ListProbeValues(PROBE_ID_AVG)))
	        .thenAnswer(new Answer<ListProbeValues>() {

			@Override
			public ListProbeValues answer(InvocationOnMock invocation) throws Throwable {
				redisMap.put(PROBE_ID_AVG, invocation.getArgument(0));
				return invocation.getArgument(0);
			}
		});
		producer.send(new GenericMessage<Probe>(probeNoValues), consumerBindingName);
		
		// теперь уже есть продьюсинг и поэтому мы что-то получаем, не null
		Message<byte[]> message = consumer.receive(100, producerBindingName ); // чтобы проверить что вернет
		assertNotNull(message); // тут у нас есть наше полученное среднее значение
		ObjectMapper mapper = new ObjectMapper();
		assertEquals(probeAvg, mapper.readValue(message.getPayload(), Probe.class));
		assertEquals(VALUE, redisMap.get(PROBE_ID_NO_AVG).getValues().get(0)); // а значение в маp появилось
	}
	
}


