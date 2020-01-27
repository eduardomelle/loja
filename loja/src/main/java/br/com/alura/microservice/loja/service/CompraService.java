package br.com.alura.microservice.loja.service;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;

import br.com.alura.microservice.loja.client.FornecedorClient;
import br.com.alura.microservice.loja.client.TransportadorClient;
import br.com.alura.microservice.loja.dto.CompraDTO;
import br.com.alura.microservice.loja.dto.InfoEntregaDTO;
import br.com.alura.microservice.loja.dto.InfoFornecedorDTO;
import br.com.alura.microservice.loja.dto.InfoPedidoDTO;
import br.com.alura.microservice.loja.dto.VoucherDTO;
import br.com.alura.microservice.loja.model.Compra;
import br.com.alura.microservice.loja.model.CompraState;
import br.com.alura.microservice.loja.repository.CompraRepository;

@Service
public class CompraService {
	
	private final static Logger LOG = LoggerFactory.getLogger(CompraService.class);

	@Autowired
	private RestTemplate client;

	@Autowired
	private DiscoveryClient eurekaClient;
	
	@Autowired
	private FornecedorClient fornecedorClient;
	
	@Autowired
	private CompraRepository compraRepository;
	
	@Autowired
	private TransportadorClient transportadorClient;

	@HystrixCommand(fallbackMethod = "realizaCompraFallback", threadPoolKey = "realizaCompraThreadPool")
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
		
		Compra compraSalva = new Compra();
		compraSalva.setState(CompraState.RECEBIDO);
		compraSalva.setEnderecoDestino(compra.getEndereco().toString());
		compraRepository.save(compraSalva);
		
		compra.setCompraId(compraSalva.getId());
		
		final String estado = compra.getEndereco().getEstado();
		
		LOG.info("Buscando informações do fornecedor de {}", estado);
		
		InfoFornecedorDTO info = fornecedorClient.getInfoPorEstado(estado);
		System.err.println(info.getEndereco());
		
		LOG.info("Realizando um pedido");
		
		InfoPedidoDTO pedido = fornecedorClient.realizaPedido(compra.getItens());
		System.err.println(pedido.getId() + " | " + pedido.getTempoDePreparo());
		
		compraSalva.setState(CompraState.PEDIDO_REALIZADO);
		compraSalva.setPedidoId(pedido.getId());
		compraSalva.setTempoDePreparo(pedido.getTempoDePreparo());
		compraRepository.save(compraSalva);
		
		InfoEntregaDTO entregaDTO = new InfoEntregaDTO();
		entregaDTO.setDataParaEntrega(LocalDate.now().plusDays(pedido.getTempoDePreparo()));
		entregaDTO.setEnderecoDestino(compra.getEndereco().toString());
		entregaDTO.setEnderecoOrigem(info.getEndereco());
		entregaDTO.setPedidoId(pedido.getId());
		
		VoucherDTO voucher = transportadorClient.reservaEntrega(entregaDTO);
		
		compraSalva.setState(CompraState.RESERVA_ENTREGA_REALIZADA);
		compraSalva.setDataParaEntrega(voucher.getPrevisaoParaEntrega());
		compraSalva.setVoucher(voucher.getNumero());
		compraRepository.save(compraSalva);
		
		return compraSalva;
	}
	
	public Compra reprocessaCompra(Long id) {
		return null;
	}
	
	public Compra cancelaCompra(Long id) {
		return null;
	}
	
	public Compra realizaCompraFallback(CompraDTO compra) {
		if (compra.getCompraId() != null) {
			return compraRepository.findById(compra.getCompraId()).get();
		}
		
		Compra compraFallback = new Compra();
		compraFallback.setEnderecoDestino(compra.getEndereco().toString());
		return compraFallback;
	}
	
	@HystrixCommand(threadPoolKey = "getByIdThreadPool")
	public Compra getById(Long id) {
		return compraRepository.findById(id).orElse(new Compra());
	}
	
}
