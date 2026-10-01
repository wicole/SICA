import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

public class Cliente {

    // Endereço do computador onde o servidor está executando.
    private static final String ENDERECO_SERVIDOR = "localhost";

    // Porta utilizada pelo servidor para receber conexões.
    private static final int PORTA = 5000;

    // Pasta utilizada para armazenar os arquivos baixados.
    private static final String PASTA_ARQUIVOS = "../arquivos_cliente";

    public static void main(String[] args) {

        /*
         * Cria uma conexão TCP com o servidor.
         * O DataInputStream é utilizado para receber dados
         * e o DataOutputStream para enviar dados.
         */
        try (
            Socket socket = new Socket(ENDERECO_SERVIDOR, PORTA);
            DataInputStream entrada =
                    new DataInputStream(socket.getInputStream());
            DataOutputStream saida =
                    new DataOutputStream(socket.getOutputStream());
            Scanner scanner = new Scanner(System.in)
        ) {

            System.out.println("====================================");
            System.out.println("           CLIENTE SICA");
            System.out.println("====================================");
            System.out.println("Conectado ao servidor!");

            boolean executando = true;

            /*
             * Mantém o menu disponível enquanto o usuário
             * não escolher a opção de sair.
             */
            while (executando) {

                exibirMenu();

                System.out.print("Digite uma opção: ");
                String opcao = scanner.nextLine();

                switch (opcao) {

                    case "1":
                        enviarArquivo(entrada, saida, scanner);
                        break;

                    case "2":
                        listarArquivos(entrada, saida);
                        break;

                    case "3":
                        baixarArquivo(entrada, saida, scanner);
                        break;

                    case "4":

                        /*
                         * Informa ao servidor que o cliente
                         * deseja encerrar a conexão.
                         */
                        saida.writeUTF("SAIR");
                        saida.flush();

                        System.out.println(
                                "Servidor: " + entrada.readUTF()
                        );

                        executando = false;
                        break;

                    default:
                        System.out.println("Opção inválida.");
                }
            }

        } catch (IOException e) {

            System.out.println(
                    "Erro na comunicação com o servidor: "
                    + e.getMessage()
            );
        }
    }

    /**
     * Exibe as opções disponíveis no sistema.
     */
    private static void exibirMenu() {

        System.out.println();
        System.out.println("====================================");
        System.out.println("              MENU");
        System.out.println("====================================");
        System.out.println("1 - Enviar arquivo");
        System.out.println("2 - Listar arquivos");
        System.out.println("3 - Baixar arquivo");
        System.out.println("4 - Sair");
        System.out.println("====================================");
    }

    /**
     * Envia um arquivo selecionado pelo usuário para o servidor.
     *
     * O cliente informa o nome e o tamanho do arquivo e,
     * em seguida, envia seus dados em blocos de 4096 bytes.
     */
    private static void enviarArquivo(
            DataInputStream entrada,
            DataOutputStream saida,
            Scanner scanner) throws IOException {

        // Informa ao servidor que será realizado um envio.
        saida.writeUTF("ENVIAR");
        saida.flush();

        System.out.print(
                "Digite o caminho do arquivo que deseja enviar: "
        );

        String caminhoArquivo = scanner.nextLine();

        File arquivo = new File(caminhoArquivo);

        /*
         * Verifica se o arquivo informado realmente existe
         * e se corresponde a um arquivo válido.
         */
        if (!arquivo.exists() || !arquivo.isFile()) {

            System.out.println("Arquivo não encontrado.");
            return;
        }

        // Envia o nome do arquivo para o servidor.
        saida.writeUTF(arquivo.getName());

        // Envia o tamanho do arquivo.
        saida.writeLong(arquivo.length());

        /*
         * Abre o arquivo e envia seus dados em blocos.
         * O uso de blocos evita carregar o arquivo inteiro
         * na memória.
         */
        try (FileInputStream arquivoEntrada =
                     new FileInputStream(arquivo)) {

            byte[] buffer = new byte[4096];
            int bytesLidos;

            while ((bytesLidos = arquivoEntrada.read(buffer)) != -1) {

                saida.write(buffer, 0, bytesLidos);
            }
        }

        saida.flush();

        // Aguarda a confirmação do servidor.
        System.out.println(entrada.readUTF());
    }

    /**
     * Solicita ao servidor a lista de arquivos disponíveis.
     */
    private static void listarArquivos(
            DataInputStream entrada,
            DataOutputStream saida) throws IOException {

        // Solicita a listagem dos arquivos.
        saida.writeUTF("LISTAR");
        saida.flush();

        // Recebe a quantidade de arquivos disponíveis.
        int quantidade = entrada.readInt();

        System.out.println();
        System.out.println("===== ARQUIVOS DO SERVIDOR =====");

        if (quantidade == 0) {

            System.out.println("Nenhum arquivo disponível.");

        } else {

            /*
             * Recebe o nome de cada arquivo e apresenta
             * na tela para o usuário.
             */
            for (int i = 0; i < quantidade; i++) {

                String nomeArquivo = entrada.readUTF();

                System.out.println(
                        (i + 1) + " - " + nomeArquivo
                );
            }
        }

        System.out.println("=================================");
    }

    /**
     * Solicita um arquivo ao servidor e salva o arquivo
     * recebido na pasta de arquivos do cliente.
     */
    private static void baixarArquivo(
            DataInputStream entrada,
            DataOutputStream saida,
            Scanner scanner) throws IOException {

        // Informa ao servidor que deseja realizar um download.
        saida.writeUTF("BAIXAR");
        saida.flush();

        System.out.print(
                "Digite o nome do arquivo que deseja baixar: "
        );

        String nomeArquivo = scanner.nextLine();

        // Envia o nome do arquivo solicitado.
        saida.writeUTF(nomeArquivo);
        saida.flush();

        /*
         * O servidor informa se o arquivo solicitado existe.
         */
        boolean arquivoExiste = entrada.readBoolean();

        if (!arquivoExiste) {

            System.out.println(
                    "Arquivo não encontrado no servidor."
            );

            return;
        }

        // Recebe o tamanho do arquivo.
        long tamanhoArquivo = entrada.readLong();

        /*
         * Cria a pasta de arquivos do cliente caso ela ainda
         * não exista.
         */
        File pastaCliente = new File(PASTA_ARQUIVOS);

        if (!pastaCliente.exists()) {
            pastaCliente.mkdirs();
        }

        /*
         * Utiliza somente o nome do arquivo, evitando que
         * caminhos externos sejam utilizados como destino.
         */
        String nomeSeguro = new File(nomeArquivo).getName();

        File arquivoDestino =
                new File(pastaCliente, nomeSeguro);

        /*
         * Recebe os dados enviados pelo servidor e grava
         * o conteúdo no arquivo de destino.
         */
        try (FileOutputStream arquivoSaida =
                     new FileOutputStream(arquivoDestino)) {

            byte[] buffer = new byte[4096];
            long recebido = 0;

            while (recebido < tamanhoArquivo) {

                int bytesParaLer = (int) Math.min(
                        buffer.length,
                        tamanhoArquivo - recebido
                );

                int bytesLidos = entrada.read(
                        buffer,
                        0,
                        bytesParaLer
                );

                /*
                 * Caso a conexão seja interrompida antes
                 * do término do arquivo, gera uma exceção.
                 */
                if (bytesLidos == -1) {

                    throw new IOException(
                            "Conexão interrompida durante o download."
                    );
                }

                arquivoSaida.write(
                        buffer,
                        0,
                        bytesLidos
                );

                recebido += bytesLidos;
            }
        }

        System.out.println(
                "Arquivo baixado com sucesso: "
                        + nomeSeguro
        );
    }
}