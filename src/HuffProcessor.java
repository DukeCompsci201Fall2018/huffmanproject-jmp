import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	private static final int SINGLE_BIT = 1;
	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
		int[] counts = getFrequencies(in);
		
		
		HuffNode root = createTree(counts);
		
		String[] codings = new String[ALPH_SIZE + 1];
		createEncodings(root, "", codings);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeTree(root, out);
		
		in.reset();
		writeCodings(codings, in, out);
		out.close();
	}
	
	private void writeCodings(String[] codings, BitInputStream in, BitOutputStream out) {
		while(true) {
			int bits = in.readBits(BITS_PER_WORD);
			if(bits == -1) {
				String code = codings[PSEUDO_EOF];
				out.writeBits(code.length(), Integer.parseInt(code,2));
				return;
			}
			String code = codings[bits];
			out.writeBits(code.length(), Integer.parseInt(code,2));
		}	
	}
	
	private void writeTree(HuffNode root, BitOutputStream out) {
		if(root.myLeft == null && root.myRight == null){
			out.writeBits(SINGLE_BIT, 1);
			out.writeBits(BITS_PER_WORD + 1, root.myValue);
			return;
		}
		out.writeBits(SINGLE_BIT, 0);
		writeTree(root.myLeft, out);
		writeTree(root.myRight, out);
	}
	
	private void createEncodings(HuffNode root, String path, String[] codings) {
		if(root.myLeft == null && root.myRight == null) {
			codings[root.myValue] = path;
			return;
		}
		createEncodings(root.myLeft, path + "0", codings);
		createEncodings(root.myRight, path + "1", codings);
	}
	
	private HuffNode createTree(int[] counts){
		PriorityQueue<HuffNode> pq = new PriorityQueue<HuffNode>();
		
		for(int i = 0; i < counts.length; i++) {
			if(counts[i] > 0) {
				pq.add(new HuffNode(i, counts[i]));
			}
		}
		
		while(pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			pq.add(new HuffNode(0, left.myWeight + right.myWeight, left, right));
		}
		return pq.remove();
	}
	
	private int[] getFrequencies(BitInputStream in) {
		int[] counts = new int[ALPH_SIZE + 1];
		
		while(true) {
			int bits = in.readBits(BITS_PER_WORD);
			if(bits == -1) {
				counts[PSEUDO_EOF] = 1;
				return counts;
			}
			counts[bits]++;
		}
	}
	
	
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){

		int bits = in.readBits(BITS_PER_INT);
		if(bits != HUFF_TREE) {
			throw new HuffException("illegal header starts with " + bits);
		}
		
		HuffNode root = readTreeHeader(in);
		writeOutput(root, in, out);
		out.close();
	}
	
	private void writeOutput(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode current = root;
		while (true){
			int bits = in.readBits(SINGLE_BIT);
			if (bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			else if(bits == 0) {
					current = current.myLeft;
				}
				
			else{
					current = current.myRight;
			}
				
			//covert bits to character if we are at a leaf
			if(current.myLeft == null && current.myRight == null) {
				if (current.myValue == PSEUDO_EOF) {
					break;
				}
				out.writeBits(BITS_PER_WORD, current.myValue);
		        current = root; // start back after leaf
			}
		}
	}
	
	private HuffNode readTreeHeader(BitInputStream in) {
		int bit = in.readBits(SINGLE_BIT);
		if(bit == -1) {
			throw new HuffException("error decoding huff tree");
		}
		if(bit == 0) {
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0,0, left, right);
		}
		else {
			int bits = in.readBits(BITS_PER_WORD + 1);
			return new HuffNode(bits, 0);
		}
	}
}