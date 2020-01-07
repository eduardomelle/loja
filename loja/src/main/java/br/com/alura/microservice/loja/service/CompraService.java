package br.com.alura.microservice.loja.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;

import br.com.alura.microservice.loja.client.FornecedorClient;
import br.com.alura.microservice.loja.dto.CompraDTO;
import br.com.alura.microservice.loja.dto.InfoFornecedorDTO;
import br.com.alura.microservice.loja.dto.InfoPedidoDTO;
import br.com.alura.microservice.loja.model.Compra;

@Service
public class CompraService {
	
	private final static Logger LOG = LoggerFactory.getLogger(CompraService.class);

	@Autowired
	private RestTemplate client;

	@Autowired
	private DiscoveryClient eurekaClient;
	
	@Autowired
	private FornecedorClient fornecedorClient;

	@HystrixCommand(fallbackMethod = "realizaCompraFallback")
	public Compra realizaCompra(CompraDTO compra) {
		/*
		ResponseEntity<InfoFornecedorDTO> exchange = client.exchange(
				"http://fornecedor/info/" + compra.getEndereco().getEstado(), HttpMethod.GET, null,
				InfoFornecedorDTO.class);
		System.err.println(exchange.getBody().getEndereco());

		eurekaClient.getInstances("fornecedor").stream().forEach(fornecedor -> {
			System.err.println("localhost:" + fornecedor.getPort());
		});
		*/
		
		final String estado = compra.getEndereco().getEstado();
		
		LOG.info("Buscando informações do fornecedor de {}", estado);
		
		InfoFornecedorDTO info = fornecedorClient.getInfoPorEstado(estado);
		System.err.println(info.getEndereco());
		
		LOG.info("Realizando um pedido");
		
		InfoPedidoDTO pedido = fornecedorClient.realizaPedido(compra.getItens());
		System.err.println(pedido.getId() + " | " + pedido.getTempoDePreparo());
		
		Compra compraSalva = new Compra();
		compraSalva.setEnderecoDestino(compra.getEndereco().toString());
		compraSalva.setPedidoId(pedido.getId());
		compraSalva.setTempoDePreparo(pedido.getTempoDePreparo());
		
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		return compraSalva;
	}
	
	public Compra realizaCompraFallback(CompraDTO compra) {
		Compra compraFallback = new Compra();
		compraFallback.setEnderecoDestino(compra.getEndereco().toString());
		return compraFallback;
	}

}
