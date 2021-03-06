package ni.gob.minsa.lab.printerserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.print.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.util.*;

/**
 * Created by FIRSTICT on 9/22/2016.
 * V1.0
 */
public class Server {

    static int port = 13001; //por defecto puerto 13001
    static String printerName = "ZDesigner GC420t (EPL"; //por defecto el nombre de la impresora es 'zebra'
    public static void main(String[] args) throws Exception {
        String pName = getProperty("printer.name");
        if (pName!=null) printerName = pName;

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        System.out.println("server started at " + port);
        server.createContext("/", new RootHandler());
        server.createContext("/print", new PrintHandler());
        server.setExecutor(null); // creates a default executor
        server.start();
    }

    public static String getProperty(String propertyName) {
        try {
            Properties mainProperties = new Properties();

            FileInputStream file;

            String path = "printer.properties";
            file = new FileInputStream(path);
            mainProperties.load(file);
            file.close();
            return mainProperties.getProperty(propertyName);
        }catch (Exception ex){
            ex.printStackTrace();
            return null;
        }
    }

    static class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            String response = "<h1>Servidor iniciado exitosamente</h1>" + "<h1>Puerto: " + port + "</h1><h1>Impresora: "+printerName + "</h1>";
            httpExchange.sendResponseHeaders(200, response.length());
            OutputStream os = httpExchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    private static class PrintHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            // parse request
            Map<String, Object> parameters = new HashMap<String, Object>();
            URI requestedUri = httpExchange.getRequestURI();
            String query = requestedUri.getRawQuery();
            parseQuery(query, parameters);

            String response = "";
            try {
                //String printerName = "ZDesigner GC420t (EPL)";
                PrintService psZebra = getPrintService(printerName);
                if (psZebra == null) {
                    System.out.println("Impresora no encontrada: "+printerName);
                    response+= "Impresora no encontrada: "+printerName;
                }else{
                    if (parameters.get("barcodes")!=null) {
                        String barcodeParam = parameters.get("barcodes").toString();
                        //String copias = (parameters.get("copias")!=null?parameters.get("copias").toString():"1");
                        //tring tipo = parameters.get("tipo").toString();
                        System.out.println(barcodeParam);
                        String[] barcodes = barcodeParam.split(",");
                        DocPrintJob job = psZebra.createPrintJob();

                        PrintLabels(job, barcodes);
                        /*if (tipo.equalsIgnoreCase("1")) {
                            PrintLabelsQRCode(job, barcodes);
                        }else if (tipo.equalsIgnoreCase("2")){
                            PrintLabelsLinealCode(job, barcodes);
                        }*/
                        response = "OK";
                    }else{
                        System.out.println("Par�metro no encontrado");
                        response+= "Par�metro no encontrado";
                    }
                }
            }catch (Exception ex){
                System.out.println("error");
                ex.printStackTrace();
                response+="Error en el proceso de impresi�n";
            }finally {
                //enviando respuesta al cliente
                httpExchange.sendResponseHeaders(200, response.length());
                OutputStream os = httpExchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        }

        private static PrintService getPrintService(String printerName){
            PrintService psZebra = null;
            PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
            for (PrintService service : services) {
                if (service.getName().equalsIgnoreCase(printerName)) {
                    psZebra = service;
                    System.out.println("Se ha encontrado impresora: " + printerName);
                    break;
                }
            }
            return psZebra;
        }

        private static void PrintLabels(DocPrintJob job, String[] barcodes) throws Exception{
            String labels="";
            for(String barcode: barcodes){
                String partes[] = barcode.split("\\*");
                //si es 1 es QrCode, va la fif, fechacaso y codigo lab
                if (partes[partes.length-1].equalsIgnoreCase("1")) {
                    labels += "N\n" +
                            "b45,0,Q,\"" + partes[0]+ partes[1] + "\"\n";
                    //sacar solo codigo del participante del codigo lab
                    labels += "A135,10,0,2,1,1,N,\"" + partes[1].substring(0,partes[1].indexOf(".")) + "\"\n";
                    int yposicion = 30;
                    //codigo lab y codigo casa
                    for (int i = 1; i < partes.length - 2; i++) {
                        labels += "A135," + String.valueOf(yposicion) + ",0,2,1,1,N,\"" + partes[i] + "\"\n";
                        yposicion += 20;
                    }
                    labels+="\nP"+partes[partes.length-2]+",1\n";
                    //si es 2 es codabar y solo va el codigo del codigo del participante
                }else if (partes[partes.length-1].equalsIgnoreCase("2")) {
                    labels += "N\n" +
                            "B20,5,0,K,2,8,70,N,\"A" + partes[0] + "A\"\n" +
                            "A235,15,0,2,1,1,N,\"" + partes[0] + "\"\n" +
                            "\nP" + partes[partes.length - 2] + ",1\n";
                }
            }
            byte[] by = labels.getBytes();
            DocFlavor flavor = DocFlavor.BYTE_ARRAY.AUTOSENSE;
            Doc doc = new SimpleDoc(by, flavor, null);
            System.out.println("imprimiendo ...");
            job.print(doc, null);
            System.out.println("etiquetas impresas");

        }

        private static void PrintLabelsQRCode(DocPrintJob job, String[] barcodes) throws Exception{
            String labels="";
            for(String barcode: barcodes){
                String partes[] = barcode.split("\\*");
                labels += "N\n" +
                        "b20,0,Q,\"" +  partes[0]+partes[1]+" "+partes[2] + "\"\n";
                int yposicion=20;
                for(int i =0; i < partes.length-1;i++) {
                    labels+="A150,"+String.valueOf(yposicion)+",0,2,1,1,N,\"" + partes[i] + "\"\n";
                    yposicion+=20;
                }
                labels+="\nP"+partes[partes.length-1]+",1\n";

            }
            byte[] by = labels.getBytes();
            DocFlavor flavor = DocFlavor.BYTE_ARRAY.AUTOSENSE;
            Doc doc = new SimpleDoc(by, flavor, null);
            System.out.println("imprimiendo ...");
            job.print(doc, null);
            System.out.println("etiquetas impresas");
        }

        private static void PrintLabelsLinealCode(DocPrintJob job, String[] barcodes) throws Exception{
            String labels="";
            for(String barcode: barcodes){
                String partes[] = barcode.split("\\*");
                labels += "N\n" +
                        "B20,5,0,K,2,8,70,N,\"A" +  partes[0]+ "A\"\n" +
                        "A235,15,0,2,1,1,N,\"" + partes[0] + "\"\n" +
                        "\nP"+partes[partes.length-1]+",1\n";

            }
            byte[] by = labels.getBytes();
            DocFlavor flavor = DocFlavor.BYTE_ARRAY.AUTOSENSE;
            Doc doc = new SimpleDoc(by, flavor, null);
            System.out.println("imprimiendo ...");
            job.print(doc, null);
            System.out.println("etiquetas impresas");
        }

        public static void parseQuery(String query, Map<String,
                Object> parameters) throws UnsupportedEncodingException {

            if (query != null) {
                String pairs[] = query.split("[&]");
                for (String pair : pairs) {
                    String param[] = pair.split("[=]");
                    String key = null;
                    String value = null;
                    if (param.length > 0) {
                        key = URLDecoder.decode(param[0],
                                System.getProperty("file.encoding"));
                    }

                    if (param.length > 1) {
                        value = URLDecoder.decode(param[1],
                                System.getProperty("file.encoding"));
                    }

                    if (parameters.containsKey(key)) {
                        Object obj = parameters.get(key);
                        if (obj instanceof List<?>) {
                            List<String> values = (List<String>) obj;
                            values.add(value);

                        } else if (obj instanceof String) {
                            List<String> values = new ArrayList<String>();
                            values.add((String) obj);
                            values.add(value);
                            parameters.put(key, values);
                        }
                    } else {
                        parameters.put(key, value);
                    }
                }
            }
        }
    }
}
