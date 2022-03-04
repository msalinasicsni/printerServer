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
    static String printerName = "ZDesigner GC420t (EPL)"; //por defecto el nombre de la impresora es 'zebra'
    static String xPosicionBarcode = "18";
    static String xPosicionText = "102";
    static String xPosicionLineal = "20";
    static String xPosicionTextLineal = "180";
    public static void main(String[] args) throws Exception {
        String pName = getProperty("printer.name");
        String pPort = getProperty("port");

        String pXPosicionBarcode = getProperty("posicion.x.barcode");
        String pXPosicionText = getProperty("posicion.x.text");

        String pXPosicionLineal = getProperty("posicion.x.Lineal");
        String pXPosicionTextLineal  = getProperty("posicion.x.textLineal");

        if (pName!=null) printerName = pName;
        if (pPort!=null) port = Integer.parseInt(pPort);
        if (pXPosicionBarcode!=null) xPosicionBarcode = pXPosicionBarcode;
        if (pXPosicionText!=null) xPosicionText = pXPosicionText;
        if (pXPosicionLineal!=null) xPosicionLineal = pXPosicionLineal;
        if (pXPosicionTextLineal!=null) xPosicionTextLineal = pXPosicionTextLineal;

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
            httpExchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            httpExchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            httpExchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
            httpExchange.getResponseHeaders().add("Access-Control-Allow-Credentials", "true");
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
                        response = "OK";
                    }else{
                        System.out.println("Parametro no encontrado");
                        response+= "Parametro no encontrado";
                    }
                }
            }catch (Exception ex){
                System.out.println("error");
                ex.printStackTrace();
                response+="Error en el proceso de impresion";
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
                            "b"+xPosicionBarcode+",5,Q,\"" + partes[0]+ partes[1] + "\"\n";
                    //sacar solo codigo del participante del codigo lab
                    String codigoPart = partes[1].replaceAll("A2.", "");
                    codigoPart = codigoPart.substring(0, codigoPart.indexOf("."));
                    labels += "A"+xPosicionText+",10,0,3,1,1,N,\"" + codigoPart + "\"\n";
                    int yposicion = 30;
                    //codigo lab y codigo casa
                    for (int i = 1; i < partes.length - 2; i++) {
                        labels += "A"+xPosicionText+"," + String.valueOf(yposicion) + ",0,2,1,1,N,\"" + partes[i] + "\"\n";
                        yposicion += 22;
                    }
                    labels += "A"+xPosicionText+"," + String.valueOf(yposicion) + ",0,4,1,1,N,\"" + "A2CARES" + "\"\n";
                    labels+="\nP"+partes[partes.length-2]+",1\n";
                    //si es 2 es codabar y solo va el codigo del codigo del participante
                }else if (partes[partes.length-1].equalsIgnoreCase("2")) {
                    labels += "N\n" +
                            "B"+xPosicionLineal+",5,0,K,2,8,70,N,\"A" + partes[1] + "A\"\n" + /*se cambio a partes[1] antes tenia partes[0]*/
                            "A"+xPosicionTextLineal+",15,0,2,1,1,N,\"" + partes[1] + "\"\n" +
                            "\nP" + partes[partes.length - 2] + ",1\n";
                }
            }
            //System.out.print(labels);
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
