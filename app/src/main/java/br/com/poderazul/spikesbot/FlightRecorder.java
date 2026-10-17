package br.com.poderazul.spikesbot;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.HandlerThread;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.*;

/** Separate disk IO from the Android screen capture/vision worker. */
final class FlightRecorder {
    private static final int MAX_IMAGES=70;
    private static final long IMAGE_MS=420, DECISION_MS=120;
    private static final AtomicInteger queued=new AtomicInteger();
    private static HandlerThread thread;
    private static Handler writer;
    private static long session=-1, lastDecision=0, lastImage=0;
    private FlightRecorder(){}

    private static File folder(Context c){return new File(c.getFilesDir(),"flight_debug");}
    private static void init(){
        if(writer!=null)return;
        thread=new HandlerThread("SpikesBot-blackbox-io");
        thread.start();
        writer=new Handler(thread.getLooper());
    }
    private static void enqueue(Runnable job){
        if(queued.get()>=18)return;
        queued.incrementAndGet();
        if(!writer.post(()->{
            try{job.run();}finally{queued.decrementAndGet();}
        }))queued.decrementAndGet();
    }
    private static void newSession(Context c,long id){
        init();
        if(session==id)return;
        session=id;lastDecision=0;lastImage=0;
        Context app=c.getApplicationContext();
        enqueue(()->{
            File[] f=folder(app).listFiles();
            if(f!=null)for(File item:f)if(item.isFile())item.delete();
        });
    }
    private static String q(String s){
        if(s==null)return "\"\"";
        return "\""+s.replace("\"","\"\"").replace("\n"," ").replace("\r"," ")+"\"";
    }
    static synchronized void capture(Context context,Bitmap frame,VisionEngine.Frame state,
                                      long now,long id,int touches) {
        if(id<=0||!state.boardFound||!state.birdFound)return;
        newSession(context,id);
        if(now-lastDecision<DECISION_MS)return;
        lastDecision=now;
        boolean imageDue=now-lastImage>=IMAGE_MS;
        if(imageDue)lastImage=now;
        if(queued.get()>=16)return;
        // Pixel copy is cheap; JPEG compression and disk writes are off-thread.
        Bitmap jpg=imageDue?frame.copy(Bitmap.Config.ARGB_8888,false):null;
        long t=Math.max(0,now-id);
        String line=t+";true;true;"+state.jump+";"+(int)state.birdX+";"+
                (int)state.birdY+";"+(int)state.targetY+";"+touches+";"+
                q(state.description)+"\n";
        Context app=context.getApplicationContext();
        enqueue(()->{
            try{
                File dir=folder(app);
                if(!dir.exists()&&!dir.mkdirs())return;
                File csv=new File(dir,"decisoes.csv");
                boolean first=!csv.exists();
                try(FileWriter out=new FileWriter(csv,true)){
                    if(first)out.write("tempo_ms;campo;passaro;pular;bird_x;bird_y;alvo_y;toques;decisao\n");
                    out.write(line);
                }
                if(jpg!=null){
                    File f=new File(dir,String.format(Locale.US,"quadro_%010d.jpg",t));
                    try(FileOutputStream o=new FileOutputStream(f)){
                        jpg.compress(Bitmap.CompressFormat.JPEG,52,o);
                    }
                    File[] all=dir.listFiles((d,n)->n.endsWith(".jpg"));
                    if(all!=null&&all.length>MAX_IMAGES){
                        Arrays.sort(all,Comparator.comparing(File::getName));
                        for(int i=0;i<all.length-MAX_IMAGES;i++)all[i].delete();
                    }
                }
            }catch(IOException ignored) {
            }finally{
                if(jpg!=null&&!jpg.isRecycled())jpg.recycle();
            }
        });
    }
    static synchronized void gesture(Context c,long now,long id,String kind){
        if(id<=0)return;
        newSession(c,id);
        Context app=c.getApplicationContext();
        String row=Math.max(0,now-id)+";"+kind+"\n";
        enqueue(()->{
            try{
                File dir=folder(app);
                if(!dir.exists()&&!dir.mkdirs())return;
                File f=new File(dir,"gestos.csv");
                boolean first=!f.exists();
                try(FileWriter out=new FileWriter(f,true)){
                    if(first)out.write("tempo_ms;evento\n");
                    out.write(row);
                }
            }catch(IOException ignored){}
        });
    }
    static File export(Context c)throws IOException {
        Handler h;
        synchronized(FlightRecorder.class){h=writer;}
        if(h!=null){
            CountDownLatch done=new CountDownLatch(1);
            h.post(done::countDown);
            try{
                if(!done.await(3,TimeUnit.SECONDS))
                    throw new IOException("Arquivos ainda sendo escritos; tente novamente");
            }catch(InterruptedException e){
                Thread.currentThread().interrupt();
                throw new IOException("Operação interrompida",e);
            }
        }
        File[] items=folder(c).listFiles((d,n)->n.endsWith(".jpg")||
                n.equals("decisoes.csv")||n.equals("gestos.csv"));
        if(items==null||items.length==0)return null;
        File target=new File(c.getCacheDir(),"shared_debug");
        if(!target.exists()&&!target.mkdirs())throw new IOException("Pasta temporária");
        File zipFile=new File(target,"SpikesBot-caixa-preta.zip");
        try(ZipOutputStream out=new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(zipFile)))){
            String info="SpikesBot v1.9\n"+
                    "decisoes.csv: recomendacoes visuais aproximadas a cada 120 ms\n"+
                    "gestos.csv: gestos enviados/concluidos/cancelados pelo Android\n"+
                    "JPEG: quadros recentes. Tudo fica no celular ate compartilhar.\n";
            out.putNextEntry(new ZipEntry("LEIA-ME.txt"));
            out.write(info.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            Arrays.sort(items,Comparator.comparing(File::getName));
            byte[] buffer=new byte[8192];
            for(File file:items){
                out.putNextEntry(new ZipEntry(file.getName()));
                try(FileInputStream in=new FileInputStream(file)){
                    int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                }
                out.closeEntry();
            }
        }
        return zipFile;
    }
}
