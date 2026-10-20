package vn.offline.sokhachcuoi.v2;

import android.database.Cursor;
import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.zip.*;import javax.xml.parsers.*;import org.w3c.dom.*;

/** Minimal XLSX reader/writer: no network and no external SDK. */
public final class XlsxHelper {
  private XlsxHelper(){}
  static String esc(String s){if(s==null)return "";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
  static void put(ZipOutputStream z,String name,String body)throws Exception{z.putNextEntry(new ZipEntry(name));z.write(body.getBytes(StandardCharsets.UTF_8));z.closeEntry();}
  public static void exportGuests(DbHelper db,OutputStream out)throws Exception{
    ZipOutputStream z=new ZipOutputStream(out);
    put(z,"[Content_Types].xml","<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
    put(z,"_rels/.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
    put(z,"xl/workbook.xml","<?xml version=\"1.0\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"KhachMoi\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
    put(z,"xl/_rels/workbook.xml.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
    StringBuilder x=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
    String[] h={"STT","Họ tên","Số điện thoại","Nhóm khách","Đã mời điện thoại","Đã gửi thiệp","Số người đi","Tiền mừng","Ghi chú"}; row(x,1,h);
    Cursor c=db.getReadableDatabase().rawQuery("SELECT name,phone,group_name,called,invited,guest_count,gift,note FROM guests ORDER BY id",null);int r=2,stt=1;
    while(c.moveToNext()){row(x,r++,new String[]{String.valueOf(stt++),c.getString(0),c.getString(1),c.getString(2),c.getInt(3)==1?"Có":"Không",c.getInt(4)==1?"Có":"Không",String.valueOf(c.getInt(5)),c.isNull(6)?"":String.valueOf(c.getLong(6)),c.getString(7)});}c.close();
    x.append("</sheetData></worksheet>");put(z,"xl/worksheets/sheet1.xml",x.toString());z.finish();z.close();
  }
  static void row(StringBuilder x,int r,String[] a){x.append("<row r=\"").append(r).append("\">");for(int i=0;i<a.length;i++){String col=String.valueOf((char)('A'+i));x.append("<c r=\"").append(col).append(r).append("\" t=\"inlineStr\"><is><t>").append(esc(a[i])).append("</t></is></c>");}x.append("</row>");}
  public static int importGuests(DbHelper db,InputStream in)throws Exception{
    ZipInputStream z=new ZipInputStream(in);
    ByteArrayOutputStream sheet=new ByteArrayOutputStream();
    ByteArrayOutputStream sharedXml=new ByteArrayOutputStream();
    ZipEntry e; byte[] b=new byte[8192]; int n;
    while((e=z.getNextEntry())!=null){
      ByteArrayOutputStream target=null;
      if(e.getName().equals("xl/worksheets/sheet1.xml")) target=sheet;
      else if(e.getName().equals("xl/sharedStrings.xml")) target=sharedXml;
      if(target!=null) while((n=z.read(b))>0) target.write(b,0,n);
      z.closeEntry();
    }
    z.close();
    if(sheet.size()==0) throw new IOException("Không tìm thấy Sheet1");

    ArrayList<String> shared=new ArrayList<>();
    if(sharedXml.size()>0){
      Document sd=safeParse(sharedXml.toByteArray());
      NodeList sis=sd.getElementsByTagName("si");
      for(int i=0;i<sis.getLength();i++){
        Element si=(Element)sis.item(i);
        NodeList ts=si.getElementsByTagName("t");
        StringBuilder v=new StringBuilder();
        for(int j=0;j<ts.getLength();j++) v.append(ts.item(j).getTextContent());
        shared.add(v.toString());
      }
    }

    Document d=safeParse(sheet.toByteArray());
    NodeList rows=d.getElementsByTagName("row");
    int count=0;
    for(int i=1;i<rows.getLength();i++){
      Element row=(Element)rows.item(i);
      NodeList cells=row.getElementsByTagName("c");
      String[] vals=new String[9];
      Arrays.fill(vals,"");

      for(int j=0;j<cells.getLength();j++){
        Element ce=(Element)cells.item(j);
        int col=columnIndex(ce.getAttribute("r"));
        if(col<0 || col>=vals.length) continue;

        String type=ce.getAttribute("t");
        String value="";
        NodeList ts=ce.getElementsByTagName("t");
        NodeList vs=ce.getElementsByTagName("v");

        if("inlineStr".equals(type) && ts.getLength()>0)
          value=ts.item(0).getTextContent();
        else if(vs.getLength()>0){
          value=vs.item(0).getTextContent();
          if("s".equals(type)){
            try{
              int idx=Integer.parseInt(value.trim());
              value=(idx>=0 && idx<shared.size()) ? shared.get(idx) : "";
            }catch(Exception ex){ value=""; }
          }
        }else if(ts.getLength()>0)
          value=ts.item(0).getTextContent();

        vals[col]=value==null ? "" : value.trim();
      }

      String name=vals[1].trim();
      if(name.isEmpty()) continue;

      String group=vals[3].trim();
      if(group.isEmpty()) group="Khác";
      db.addGroup(group);

      int gc=1;
      try{
        String g=vals[6].replaceAll("[^0-9]","");
        if(!g.isEmpty()) gc=Math.max(1,Integer.parseInt(g));
      }catch(Exception ignored){}

      Long gift=null;
      try{
        String g=vals[7].replaceAll("[^0-9]","");
        if(!g.isEmpty()) gift=Long.parseLong(g);
      }catch(Exception ignored){}

      db.saveGuestV2(name,group,vals[2],yes(vals[4]),yes(vals[5]),gc,gift,vals[8]);
      count++;
    }
    return count;
  }

  static Document safeParse(byte[] xml)throws Exception{
    DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();
    try{f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);}catch(Exception ignored){}
    try{f.setFeature("http://xml.org/sax/features/external-general-entities",false);}catch(Exception ignored){}
    try{f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);}catch(Exception ignored){}
    f.setExpandEntityReferences(false);
    return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
  }

  static int columnIndex(String ref){
    int col=0,seen=0;
    for(int i=0;i<ref.length();i++){
      char ch=Character.toUpperCase(ref.charAt(i));
      if(ch<'A'||ch>'Z') break;
      col=col*26+(ch-'A'+1);
      seen++;
    }
    return seen==0 ? -1 : col-1;
  }
  static boolean yes(String s){s=s==null?"":s.trim().toLowerCase(new Locale("vi","VN"));return s.equals("có")||s.equals("co")||s.equals("1")||s.equals("x")||s.equals("yes")||s.equals("true");}
  public static void exportTemplate(OutputStream out)throws Exception{
    ZipOutputStream z=new ZipOutputStream(out);
    put(z,"[Content_Types].xml","<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
    put(z,"_rels/.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
    put(z,"xl/workbook.xml","<?xml version=\"1.0\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"KhachMoi\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
    put(z,"xl/_rels/workbook.xml.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
    StringBuilder x=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");String[] h={"STT","Họ tên","Số điện thoại","Nhóm khách","Đã mời điện thoại","Đã gửi thiệp","Số người đi","Tiền mừng","Ghi chú"};row(x,1,h);row(x,2,new String[]{"1","Nguyễn Văn A","0912345678","Bạn bè","Có","Không","2","500000","Ví dụ - có thể xóa dòng này"});x.append("</sheetData></worksheet>");put(z,"xl/worksheets/sheet1.xml",x.toString());z.finish();z.close();
  }
}
