package alxb.flighttraining.game.network;

import java.util.Locale;

/** Self-contained IPv4 invite: address + port + lobby identity + CRC16. No directory or DNS lookup. */
public final class LobbyCode {
    private static final String ALPHABET="0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    public record Target(String address,int port,String lobbyId){}
    private LobbyCode(){}
    public static String encode(String address,int port,String lobbyId){
        if(address==null||port<1||port>65535||lobbyId==null||!lobbyId.matches("[0-9a-fA-F]{8}"))throw invalid();
        String[] parts=address.split("\\.",-1);if(parts.length!=4)throw invalid();byte[] data=new byte[12];
        for(int i=0;i<4;i++){
            if(parts[i].isEmpty()||parts[i].length()>3||!parts[i].chars().allMatch(c->c>='0'&&c<='9'))throw invalid();
            int value=Integer.parseInt(parts[i]);if(value>255)throw invalid();data[i]=(byte)value;
        }
        if((data[0]&255)==0||(data[0]&255)>=224)throw invalid();
        data[4]=(byte)(port>>>8);data[5]=(byte)port;long id=Long.parseUnsignedLong(lobbyId,16);
        for(int i=0;i<4;i++)data[6+i]=(byte)(id>>>(24-8*i));
        int crc=crc(data,10);data[10]=(byte)(crc>>>8);data[11]=(byte)crc;
        StringBuilder compact=new StringBuilder();int buffer=0,bits=0;
        for(byte b:data){buffer=(buffer<<8)|(b&255);bits+=8;while(bits>=5){bits-=5;compact.append(ALPHABET.charAt((buffer>>>bits)&31));}}
        if(bits>0)compact.append(ALPHABET.charAt((buffer<<(5-bits))&31));
        StringBuilder code=new StringBuilder("DSC1");for(int i=0;i<compact.length();i++){if(i%5==0)code.append('-');code.append(compact.charAt(i));}return code.toString();
    }
    public static Target decode(String input){
        if(input==null||input.length()>128)throw invalid();StringBuilder cleaned=new StringBuilder();
        for(char c:input.toUpperCase(Locale.ROOT).toCharArray())if(c!='-'&&c!='–'&&c!='—'&&!Character.isWhitespace(c))cleaned.append(c);
        String compact=cleaned.toString();if(compact.length()==24&&compact.startsWith("DSC1"))compact=compact.substring(4);
        if(compact.length()!=20)throw invalid();byte[] data=new byte[12];int buffer=0,bits=0,index=0;
        for(char c:compact.toCharArray()){
            if(c=='O')c='0';if(c=='I'||c=='L')c='1';int value=ALPHABET.indexOf(c);if(value<0)throw invalid();buffer=(buffer<<5)|value;bits+=5;
            if(bits>=8){bits-=8;if(index>=data.length)throw invalid();data[index++]=(byte)(buffer>>>bits);}
        }
        if(index!=12||(buffer&15)!=0||crc(data,10)!=((data[10]&255)<<8|(data[11]&255)))throw invalid();
        int first=data[0]&255,port=(data[4]&255)<<8|(data[5]&255);if(first==0||first>=224||port==0)throw invalid();
        String address=first+"."+(data[1]&255)+"."+(data[2]&255)+"."+(data[3]&255);
        long id=0;for(int i=6;i<10;i++)id=(id<<8)|(data[i]&255);
        return new Target(address,port,String.format(Locale.ROOT,"%08x",id));
    }
    public static String canonical(String input){Target target=decode(input);return encode(target.address(),target.port(),target.lobbyId());}
    private static int crc(byte[] data,int length){int crc=0xffff;for(int i=0;i<length;i++){crc^=(data[i]&255)<<8;for(int j=0;j<8;j++)crc=((crc&0x8000)!=0?(crc<<1)^0x1021:crc<<1)&0xffff;}return crc;}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid lobby code. Copy the complete DSC1 code from the host.");}
}
