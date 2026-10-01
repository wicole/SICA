import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Servidor {

    // Porta utilizada pelo servidor para receber conexões.
    private static final int PORTA = 5000;

    // Pasta onde os arquivos recebidos serão armazenados.
    private static final String PASTA_ARQUIVOS = "../arquivos_servidor";

    public static void main(String[] args) {

        /*
         * Cria o servidor TCP na porta definida.
         * O servidor permanece em execução aguardando
         * conexões dos clientes.
         */
        try (ServerSocket servidor = new ServerSocket(PORTA)) {

            System.out.println("====================================");
            System.out.println("           SERVIDOR SICA");
            System.out.println("====================================");
            System.out.println("Servidor iniciado na porta " + PORTA);
            System.out.println();

            /*
             * O servidor permanece aguardando novas conexões.
             * Após um cliente sair, outro cliente poderá se conectar.
             */
            while (true) {

                System.out.println("Aguardando conexão...");

                Socket socket = servidor.accept();

                System.out.println(
                        "Cliente conectado: "
                                + socket.getInetAddress()
                );

                /*
                 * Cria os fluxos utilizados para comunicação
                 * entre o cliente e o servidor.
                 */
                try (
                    DataInputStream entrada =
                            new DataInputStream(socket.getInputStream());

                    DataOutputStream saida =
                            new DataOutputStream(socket.getOutputStream())
                ) {

                    atenderCliente(entrada, saida);

                } finally {

                    /*
                     * Fecha a conexão com o cliente após o
                     * encerramento da comunicação.
                     */
                    socket.close();

                    System.out.println("Cliente desconectado.");
                    System.out.println();
                }
            }

        } catch (IOException e) {

            System.out.println(
                    "Erro no servidor: "
                            + e.getMessage()
            );
        }
    }

    /**
     * Processa os comandos enviados pelo cliente.
     *
     * Os comandos possíveis são:
     * ENVIAR - recebe um arquivo do cliente.
     * LISTAR - lista os arquivos disponíveis.
     * BAIXAR - envia um arquivo para o cliente.
     * SAIR   - encerra a conexão.
     */
    private static void atenderCliente(
            DataInputStream entrada,
            DataOutputStream saida) throws IOException {

        boolean conectado = true;

        /*
         * Continua recebendo comandos enquanto o cliente
         * permanecer conectado.
         */
        while (conectado) {

            String comando = entrada.readUTF();

            switch (comando) {

                case "ENVIAR":

                    receberArquivo(entrada, saida);
                    break;

                case "LISTAR":

                    listarArquivos(saida);
                    break;

                case "BAIXAR":

                    enviarArquivo(entrada, saida);
                    break;

                case "SAIR":

                    System.out.println(
                            "Cliente solicitou encerramento."
                    );

                    saida.writeUTF("Conexão encerrada.");
                    saida.flush();

                    conectado = false;
                    break;

                default:

                    /*
                     * Caso o cliente envie um comando que
                     * não seja reconhecido.
                     */
                    saida.writeUTF("Comando inválido.");
                    saida.flush();
            }
        }
    }

    /**
     * Lista os arquivos disponíveis na pasta do servidor
     * e envia os nomes para o cliente.
     */
    private static void listarArquivos(
            DataOutputStream saida) throws IOException {

        File pasta = new File(PASTA_ARQUIVOS);

        /*
         * Cria a pasta caso ela ainda não exista.
         */
        if (!pasta.exists()) {
            pasta.mkdirs();
        }

        /*
         * Obtém somente os arquivos existentes na pasta.
         */
        File[] arquivos = pasta.listFiles(File::isFile);

        /*
         * Caso a pasta esteja vazia ou não possa ser lida,
         * informa ao cliente que não existem arquivos.
         */
        if (arquivos == null) {

            saida.writeInt(0);
            saida.flush();

            return;
        }

        /*
         * Envia primeiro a quantidade de arquivos.
         */
        saida.writeInt(arquivos.length);

        /*
         * Em seguida, envia o nome de cada arquivo.
         */
        for (File arquivo : arquivos) {

            saida.writeUTF(arquivo.getName());
        }

        saida.flush();
    }

    /**
     * Recebe um arquivo enviado pelo cliente.
     *
     * O cliente envia primeiro o nome do arquivo,
     * depois o tamanho e, por fim, os dados.
     */
    private static void receberArquivo(
            DataInputStream entrada,
            DataOutputStream saida) throws IOException {

        // Recebe o nome do arquivo.
        String nomeArquivo = entrada.readUTF();

        /*
         * Garante que somente o nome do arquivo seja utilizado,
         * evitando caminhos externos.
         */
        String nomeSeguro =
                new File(nomeArquivo).getName();

        // Recebe o tamanho do arquivo.
        long tamanhoArquivo = entrada.readLong();

        // Cria a pasta de armazenamento, caso necessário.
        File pasta = new File(PASTA_ARQUIVOS);

        if (!pasta.exists()) {
            pasta.mkdirs();
        }

        // Define o local onde o arquivo será salvo.
        File arquivoDestino =
                new File(pasta, nomeSeguro);

        /*
         * Recebe os dados do arquivo em blocos de 4096 bytes.
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

                if (bytesLidos == -1) {

                    throw new IOException(
                            "Conexão interrompida durante o envio."
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

        /*
         * Informa ao cliente que o arquivo foi recebido
         * corretamente.
         */
        saida.writeUTF(
                "Arquivo enviado com sucesso: "
                        + nomeSeguro
        );

        saida.flush();

        System.out.println(
                "Arquivo recebido: "
                        + nomeSeguro
        );
    }

    /**
     * Envia um arquivo existente no servidor para o cliente.
     */
    private static void enviarArquivo(
            DataInputStream entrada,
            DataOutputStream saida) throws IOException {

        // Recebe o nome do arquivo solicitado.
        String nomeArquivo = entrada.readUTF();

        /*
         * Utiliza somente o nome do arquivo para evitar
         * acesso a diretórios externos.
         */
        String nomeSeguro =
                new File(nomeArquivo).getName();

        File arquivo =
                new File(PASTA_ARQUIVOS, nomeSeguro);

        /*
         * Verifica se o arquivo solicitado existe.
         */
        if (!arquivo.exists() || !arquivo.isFile()) {

            saida.writeBoolean(false);
            saida.flush();

            System.out.println(
                    "Arquivo não encontrado: "
                            + nomeSeguro
            );

            return;
        }

        /*
         * Informa ao cliente que o arquivo existe.
         */
        saida.writeBoolean(true);

        // Envia o tamanho do arquivo.
        saida.writeLong(arquivo.length());

        /*
         * Abre o arquivo e envia seu conteúdo em blocos.
         */
        try (FileInputStream arquivoEntrada =
                     new FileInputStream(arquivo)) {

            byte[] buffer = new byte[4096];
            int bytesLidos;

            while ((bytesLidos =
                    arquivoEntrada.read(buffer)) != -1) {

                saida.write(
                        buffer,
                        0,
                        bytesLidos
                );
            }
        }

        saida.flush();

        System.out.println(
                "Arquivo enviado ao cliente: "
                        + nomeSeguro
        );
    }
}